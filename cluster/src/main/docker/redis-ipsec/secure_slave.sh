#!/bin/sh

service setkey start

service racoon restart

redis-server --slaveof 172.18.0.102 6379


