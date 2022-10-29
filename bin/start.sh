#!/bin/bash
# Starts the 'netbeans-proxies' program
rm -fr hgexternalcache
rm -f external/*.zip
java -jar target/netbeans-proxies-1.0.jar
