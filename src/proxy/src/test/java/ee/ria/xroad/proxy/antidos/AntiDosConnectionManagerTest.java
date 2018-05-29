/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.antidos;

import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test to verify correct Anti-Dos behavior.
 */
public class AntiDosConnectionManagerTest {

    private static final Set<String> KNOWN_ADDRESSES = new HashSet<>();
    static {
        KNOWN_ADDRESSES.add("test1");
        KNOWN_ADDRESSES.add("test2");
        KNOWN_ADDRESSES.add("test3");
    }

    /**
     * Set up configuration.
     */
    @BeforeClass
    public static void reloadGlobalConf() {
        GlobalConf.reload(new EmptyGlobalConf() {
            @Override
            public Set<String> getKnownAddresses() {
                return KNOWN_ADDRESSES;
            }
        });
    }

    // ------------------------------------------------------------------------

    /**
     * Test to ensure the system behaves correctly under normal load.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalLoad() throws Exception {
        TestConfiguration conf = new TestConfiguration(15, 0.5);

        TestSystemMetrics sm = new TestSystemMetrics();
        sm.addLoad(20, 0.1);

        TestSocketChannel member1 = createConnection("test1");
        TestSocketChannel member2 = createConnection("test2");
        TestSocketChannel member3 = createConnection("test3");

        TestConnectionManager cm = createConnectionManager(conf, sm);
        // two connections from member2
        cm.accept(member1, member2, member2, member3);

        // member3 should get next connection after member2's frist connection
        cm.assertConnections(member1, member2, member3, member2);

        cm.assertEmpty();
    }

    /**
     * Test to ensure the system behaves correctly when it's out of file handles.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void outOfFileHandles() throws Exception {
        TestConfiguration conf = new TestConfiguration(5, 1.1);

        TestSystemMetrics sm = new TestSystemMetrics();
        sm.addLoad(7, 0.1);
        sm.addLoad(6, 0.1);
        sm.addLoad(3, 0.1);

        TestSocketChannel member1 = createConnection("test1");
        TestSocketChannel member2 = createConnection("test2");

        TestConnectionManager cm = createConnectionManager(conf, sm);
        cm.accept(member1, member2, member2);

        cm.assertConnections(member1, member2);

        assertNull(cm.getNextConnection());
        assertTrue(member2.isClosed());

        cm.assertEmpty();
    }

    /**
     * Test to ensure the system allows known members to connect under DOS attack.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void knownMembersCanConnectUnderDosAttack() throws Exception {
        TestConfiguration conf = new TestConfiguration(5, 1.1);

        TestSystemMetrics sm = new TestSystemMetrics();
        sm.addLoad(7, 0.1);

        TestSocketChannel member1 = createConnection("test1");
        TestSocketChannel member2 = createConnection("test2");

        TestSocketChannel attacker1 = createConnection("attacker1");
        TestSocketChannel attacker2 = createConnection("attacker2");

        TestConnectionManager cm = createConnectionManager(conf, sm);

        cm.accept(
                member1,
                attacker1,
                member2,
                attacker1,
                attacker2,
                attacker2,
                member2);

        // We should get interleaved connections between members and attackers
        cm.assertConnections(
                member1,
                attacker1,
                member2,
                attacker1,
                member2,
                attacker2,
                attacker2);

        cm.assertEmpty();
    }

    // ------------------------------------------------------------------------

    private static TestConnectionManager createConnectionManager(
            TestConfiguration configuration, TestSystemMetrics systemMetrics)
                throws Exception {
        TestConnectionManager connectionManager =
                new TestConnectionManager(configuration, systemMetrics);
        connectionManager.init();
        return connectionManager;
    }

    private static TestSocketChannel createConnection(String address) {
        return new TestSocketChannel(address);
    }
}
