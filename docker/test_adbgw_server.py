import contextlib
import io
from pathlib import Path
import runpy
import unittest
from unittest.mock import Mock


server = runpy.run_path(str(Path(__file__).with_name("adbgw-server.py")))
Gateway = server["Gateway"]
Adb = server["Adb"]
discovery_rows = server["discovery_rows"]
SERIAL = "PHONE123"
PHONE = "192.0.2.10"
FIXED = f"{PHONE}:5555"
FIRST_TLS = f"{PHONE}:41000"
SECOND_TLS = f"{PHONE}:41500"
DISCOVERY = f"""List of discovered mdns services
adb-{SERIAL}-older (2)   _adb-tls-connect._tcp   {FIRST_TLS}
adb-{SERIAL}-newer       _adb-tls-connect._tcp   {SECOND_TLS}
adb-OTHER-identifier    _adb-tls-connect._tcp   192.0.2.11:41000
adb-{SERIAL}             _adb._tcp               {FIXED}
"""


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.adb = Mock()
        self.adb.run.return_value = (True, DISCOVERY)
        self.adb.ensure_helper.return_value = None
        self.gateway = Gateway(self.adb, SERIAL, PHONE)
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))

    def test_mdns_filters_device_and_accepts_duplicate_suffix(self):
        self.assertEqual(list(discovery_rows(DISCOVERY, SERIAL)), [
            ("_adb-tls-connect._tcp", PHONE, 41000),
            ("_adb-tls-connect._tcp", PHONE, 41500),
            ("_adb._tcp", PHONE, 5555),
        ])

    def test_ready_does_not_restart_adbd(self):
        self.adb.connect.return_value = True
        self.gateway.tick()

        self.assertTrue(self.gateway.status["fixed_reachable"])
        self.adb.connect.assert_called_once_with(FIXED, SERIAL)
        self.adb.ensure_helper.assert_called_once_with(FIXED)
        self.adb.run.assert_not_called()

    def test_dynamic_port_recovery_skips_stale_record(self):
        self.adb.connect.side_effect = lambda target, _: target == SECOND_TLS
        self.gateway.tick()

        self.assertFalse(self.gateway.status["fixed_reachable"])
        self.assertEqual(self.gateway.status["last_action"], "switching")
        self.adb.run.assert_called_with("-s", SECOND_TLS, "tcpip", "5555")

        self.adb.connect.side_effect = None
        self.adb.connect.return_value = True
        self.gateway.tick()
        self.assertTrue(self.gateway.status["fixed_reachable"])

    def test_recovery_without_a_configured_ip(self):
        self.gateway = Gateway(self.adb, SERIAL, "")
        self.adb.connect.side_effect = lambda target, _: target == FIXED
        self.gateway.tick()
        self.assertTrue(self.gateway.status["fixed_reachable"])
        self.assertEqual(self.gateway.status["phone"], PHONE)

    def test_offline_state_replaces_last_success(self):
        self.adb.connect.return_value = True
        self.gateway.tick()
        self.adb.connect.return_value = False
        self.adb.run.return_value = (True, "")
        self.gateway.tick()
        self.assertFalse(self.gateway.status["fixed_reachable"])
        self.assertEqual(self.gateway.status["last_action"], "waiting_for_phone")

    def test_adb_ignores_exit_success_when_the_remote_command_fails(self):
        adb = Adb("adb", 5038)
        adb.run = Mock(side_effect=[
            (False, "device not found"),
            (True, "failed to connect"),
            (False, "device not found"),
        ])
        self.assertFalse(adb.connect(FIXED, SERIAL))

    def test_gateway_can_restart_missing_helper_over_working_adb(self):
        adb = Adb("adb", 5038)
        adb.run = Mock(side_effect=[(True, "(nothing)"), (True, "Starting service")])
        self.assertIsNone(adb.ensure_helper(FIXED))
        command = adb.run.call_args.args
        self.assertIn("start-foreground-service", command)
        self.assertEqual(command[-1], "com.dsh.adbkeep/.GatewayService")


if __name__ == "__main__":
    unittest.main()
