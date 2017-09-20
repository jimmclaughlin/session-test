#!/bin/sh

service setkey start

service racoon restart

redis-server /redis/sentinel.conf --sentinel