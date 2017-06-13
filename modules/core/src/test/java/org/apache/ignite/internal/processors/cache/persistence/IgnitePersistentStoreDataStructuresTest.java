/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.persistence;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.IgniteLock;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.IgniteSet;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.MemoryConfiguration;
import org.apache.ignite.configuration.MemoryPolicyConfiguration;
import org.apache.ignite.configuration.PersistentStoreConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

/**
 *
 */
public class IgnitePersistentStoreDataStructuresTest extends GridCommonAbstractTest {
    /** */
    private static final TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        ((TcpDiscoverySpi)cfg.getDiscoverySpi()).setIpFinder(ipFinder);

        MemoryConfiguration dbCfg = new MemoryConfiguration();

        MemoryPolicyConfiguration memPlcCfg = new MemoryPolicyConfiguration();

        memPlcCfg.setName("dfltMemPlc");
        memPlcCfg.setInitialSize(200 * 1024 * 1024);
        memPlcCfg.setMaxSize(200 * 1024 * 1024);

        dbCfg.setMemoryPolicies(memPlcCfg);
        dbCfg.setDefaultMemoryPolicyName("dfltMemPlc");

        cfg.setMemoryConfiguration(dbCfg);

        cfg.setPersistentStoreConfiguration(new PersistentStoreConfiguration().setWalMode(WALMode.LOG_ONLY));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        GridTestUtils.deleteDbFiles();
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        GridTestUtils.deleteDbFiles();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testQueue() throws Exception {
        Ignite ignite = startGrids(4);

        ignite.active(true);

        IgniteQueue<Object> queue = ignite.queue("testQueue", 100, new CollectionConfiguration());

        for (int i = 0; i < 100; i++)
            queue.offer(i);

        stopAllGrids();

        ignite = startGrids(4);

        ignite.active(true);

        queue = ignite.queue("testQueue", 0, null);

        for (int i = 0; i < 100; i++)
            assertEquals(i, queue.poll());
    }

    /**
     * @throws Exception If failed.
     */
    public void testAtomic() throws Exception {
        Ignite ignite = startGrids(4);

        ignite.active(true);

        IgniteAtomicLong atomicLong = ignite.atomicLong("testLong", 0, true);

        for (int i = 0; i < 100; i++)
            atomicLong.incrementAndGet();

        stopAllGrids();

        ignite = startGrids(4);

        ignite.active(true);

        atomicLong = ignite.atomicLong("testLong", 0, false);

        for (int i = 100; i != 0; )
            assertEquals(i--, atomicLong.getAndDecrement());
    }

    /**
     * @throws Exception If failed.
     */
    public void testSequence() throws Exception {
        Ignite ignite = startGrids(4);

        ignite.active(true);

        IgniteAtomicSequence sequence = ignite.atomicSequence("testSequence", 0, true);

        int i = 0;

        while (i < 1000) {
            sequence.incrementAndGet();

            i++;
        }

        stopAllGrids();

        ignite = startGrids(4);

        ignite.active(true);

        sequence = ignite.atomicSequence("testSequence", 0, false);

        assertTrue(sequence.incrementAndGet() > i);
    }

    /**
     * @throws Exception If failed.
     */
    public void testSet() throws Exception {
        Ignite ignite = startGrids(4);

        ignite.active(true);

        IgniteSet<Object> set = ignite.set("testSet", new CollectionConfiguration());

        for (int i = 0; i < 100; i++)
            set.add(i);

        stopAllGrids();

        ignite = startGrids(4);

        ignite.active(true);

        set = ignite.set("testSet", null);

        assertFalse(set.add(99));

        for (int i = 0; i < 100; i++)
            assertTrue(set.contains(i));

        assertEquals(100, set.size());
    }

    /**
     * @throws Exception If failed.
     */
    public void testLockNotPersisted() throws Exception {
        Ignite ignite = startGrids(4);

        ignite.active(true);

        IgniteLock lock = ignite.reentrantLock("test", true, true, true);

        assert lock != null;

        stopAllGrids();

        ignite = startGrids(4);

        ignite.active(true);

        lock = ignite.reentrantLock("test", true, true, false);

        assert lock == null;
    }

    /**
     * @throws Exception If failed.
     */
    public void testAtomicSequence() throws Exception {
        Ignite ignite = startGrids(4);

        ignite.active(true);

        IgniteAtomicSequence seq = ignite.atomicSequence("test", 0, true);

        assert seq != null;

        long val = seq.addAndGet(100);

        assert val == 100;

        stopAllGrids();

        ignite = startGrids(4);

        ignite.active(true);

        seq = ignite.atomicSequence("test", 0, false);

        assert seq != null;

        assert seq.get() == Math.max(val, seq.batchSize()) : seq.get();
    }
}