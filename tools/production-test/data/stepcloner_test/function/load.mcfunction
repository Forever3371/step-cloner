scoreboard objectives add stepcloner_probe dummy
execute store success score #selftest stepcloner_probe run stepcloner selftest
execute if score #selftest stepcloner_probe matches 1 run say PASS_STEPCLONER_SELFTEST
execute unless score #selftest stepcloner_probe matches 1 run say FAIL_STEPCLONER_SELFTEST
scoreboard objectives remove stepcloner_probe