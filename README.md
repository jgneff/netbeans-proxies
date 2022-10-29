Building NetBeans in an environment that defines both proxy variables causes a brief denial-of-service (DoS) attack on one of the Web servers hosted by the Oregon State University (OSU) Open Source Lab. Below is a typical example of the variables being defined and exported to the environment:

```bash
export http_proxy=http://10.10.10.1:8222/
export https_proxy=http://10.10.10.1:8222/
```

This project builds a program, called *netbeans-proxies*, that safely illustrates the problem without creating a burden on the target server. This program downloads just 14 kilobytes in five files, while a clean build of NetBeans downloads at least 754 megabytes in 564 files.

This repository includes a simple main class along with verbatim copies of three classes from the *master* branch of the NetBeans repository:

* [Main.java](src/main/java/org/status6/netbeans/proxies/Main.java) - the main class
* [DownloadBinaries.java](src/main/java/org/netbeans/nbbuild/extlibs/DownloadBinaries.java) - the NetBeans class for the `downloadbinaries` task
* [ConfigureProxy.java](src/main/java/org/netbeans/nbbuild/extlibs/ConfigureProxy.java) - the NetBeans class for the `configureproxy` task
* [MavenCoordinate.java](src/main/java/org/netbeans/nbbuild/extlibs/MavenCoordinate.java) - a supporting NetBeans utility class

The main class executes the `DownloadBinaries` task of the NetBeans build, passing a [manifest](external/binaries-list) of five files. The manifest contains the five smallest files that are downloaded from the OSU Web server while building NetBeans, with a total size of just 13,712 bytes.

## Build

Build the program using Maven with the command:

```console
$ mvn clean package
```

## Run

Run the program on a system with OpenJDK version 11 or later with the command:

```console
$ bin/start.sh
```

The Bash script [`bin/start.sh`](bin/start.sh) removes any previously downloaded files and starts the program with the `java` command. The program loads the manifest [`external/binaries-list`](external/binaries-list), downloads each file in the manifest into the directory `external`, and caches a copy of the files in the directory `hgexternalcache`.

To run the application on another machine, synchronize the JAR file and its dependencies with the Bash script [`bin/rsync.sh`](bin/rsync.sh) followed by the remote host name, as in the example below:

```console
$ bin/rsync.sh netbeans.lxd
```

The script copies the program and its dependencies to the directory `~/src/netbeans-proxies` on the remote host.

## Test

I reproduced the problem on my Ubuntu workstation by setting up two LXD containers: one for running the Squid proxy server and the other for building NetBeans and running the program in this repository.

The Squid proxy server runs in a LXD container called *jammy* running Ubuntu 22.04 LTS (Jammy Jellyfish):

```console
$ host jammy.lxd
jammy.lxd has address 10.203.206.217
jammy.lxd has IPv6 address fd42:561e:dfee:bb12:216:3eff:fe4f:99d1
```

I run this program in an LXD container called *netbeans* running Ubuntu 20.04.5 LTS (Focal Fossa):

```console
$ host netbeans.lxd
netbeans.lxd has address 10.203.206.244
netbeans.lxd has IPv6 address fd42:561e:dfee:bb12:216:3eff:fec4:e7d7
```

I modified the Squid configuration file on *jammy* as follows:

**/etc/squid/squid.conf**
```diff
1552c1552,1558
< http_access allow localhost
---
> acl lan4 src 10.203.206.244
> acl lan6 src fd42:561e:dfee:bb12:216:3eff:fec4:e7d7
> http_access allow lan4
> http_access allow lan6
>
> # Disable caching entirely
> cache deny all
```

I created a firewall on *netbeans* to block all outbound traffic except for DNS on port 53 and the Squid proxy server on port 3128. The following script defines and enables the firewall:

**~/bin/firewall.sh**
```shell
#!/bin/bash
# Creates custom firewall rules
ufw reset
ufw default allow incoming
ufw default reject outgoing

# Allows DNS
ufw allow out to any port 53

# Allows connections to Squid proxy
ufw allow out proto tcp to 10.203.206.217 port 3128
ufw allow out proto tcp to fd42:561e:dfee:bb12:216:3eff:fe4f:99d1 port 3128

ufw enable
ufw status verbose
```

I created the file below on *netbeans* to define the proxy variables and export them to the environment:

**~/bin/proxy.env**
```bash
#!/bin/bash
# Source this file to set the proxy environment variables
export http_proxy=http://10.203.206.217:3128/
export https_proxy=http://10.203.206.217:3128/
```

After building the program and copying it to the *netbeans* container, I changed to the program's directory, sourced the file that defines the proxy variables, and started the test as follows:

```console
$ cd ~/src/netbeans-proxies
$ . ~/bin/proxy.env
$ bin/start.sh
```

On *jammy*, Squid records each proxy connection in the file **/var/log/squid/access.log** when the connection to the remote host is closed. You can watch its output in another terminal with:

```console
$ cd /var/log/squid
$ sudo tail -f access.log
```

You can stop the proxy server (which takes some time), save its log file, and restart it as shown below:

```console
$ cd /var/log/squid
$ sudo systemctl stop squid.service
  [takes about 30 seconds]
$ sudo cp access.log ~/access-backup1.log
$ sudo rm *.log
$ sudo systemctl start squid.service
```

On the *netbeans* container, you can use the following command to display all socket connections as they are destroyed (short for `ss --tcp --numeric --events`):

```console
$ ss -tnE
```

For a detailed report of all request and response headers and the data that follows, see the [`tcpflow` command](https://github.com/simsong/tcpflow). For a detailed report of each TLS handshake, see the description of the system property `javax.net.debug` in the [Debugging Utilities](https://docs.oracle.com/en/java/javase/11/security/java-secure-socket-extension-jsse-reference-guide.html#GUID-31B7E142-B874-46E9-8DD0-4E18EC0EB2CF) section of the Java Secure Socket Extension (JSSE) Reference Guide. For example:

```console
$ java -Djavax.net.debug=ssl:handshake:verbose -jar target/netbeans-proxies-1.0.jar
```

## Debug

### Problems

#### Connections

Below is an example of the proxy access log when both proxy variables are defined:

```
1666983661.938   5846 10.203.206.244 TCP_TUNNEL/200 21701 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.236.52 -
1666983677.394  21303 10.203.206.244 TCP_TUNNEL/200 6323 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.236.52 -
1666983677.733  21084 10.203.206.244 TCP_TUNNEL/200 207 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.236.52 -
1666983677.795  21070 10.203.206.244 TCP_TUNNEL/200 207 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.236.52 -
1666983677.871  21056 10.203.206.244 TCP_TUNNEL/200 207 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.236.52 -
1666983677.931  21055 10.203.206.244 TCP_TUNNEL/200 207 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.236.52 -
```

The duration of the connections (the second field, in milliseconds) indicates that none of the them were closed by the downloading task. In this example, the program makes six connections to the target Web server, but only the first is used to transfer files. The other five connections go unused and transfer no data in either direction other than the TLS handshake.

The target Apache Web server closes the downloading connection after the default [keep-alive timeout](https://httpd.apache.org/docs/2.4/mod/core.html#KeepAliveTimeout) of five seconds when it receives no further requests. The server closes the other five connections after the default [request-read header timeout](https://httpd.apache.org/docs/2.4/mod/mod_reqtimeout.html#RequestReadTimeout) of 20 seconds when it receives no request headers at all. The default values for these settings in the Apache Web server configuration are shown below:

```apache
TimeOut 60
KeepAliveTimeout 5

<IfModule reqtimeout_module>
  RequestReadTimeout handshake=0 header=20-40,MinRate=500 body=20,MinRate=500
</IfModule>
```

In the full build, the downloading task tries to make more than 1,128 socket connections: 564 direct, 564 through the `http_proxy`, and another 564 through the `https_proxy`, with the keep-alive feature reusing most of the successful ones through the proxy. In reality, only a handful of connections are required for downloading all 564 files — as few as two and perhaps as many as 12 to 24, depending on the servers' keep-alive settings. All the unnecessary connections only make the keep-alive feature less efficient.

#### Threads

Notice that the program hangs and requires `Ctrl-C` to end it. It hangs because the `openConnection` method of `ConfigureProxy` never calls `shutdownNow` on any of the `ExecutorService` instances that it creates:

```console
$ jps
2980 Jps
2917 netbeans-proxies-1.0.jar
$ jstack 2917 | grep pool
"pool-1-thread-1" #11 prio=5 os_prio=0 cpu=136.31ms elapsed=41.19s tid=0x00007fb0903fc800 nid=0x17b3 waiting on condition  [0x00007fb0659f7000]
"pool-1-thread-2" #12 prio=5 os_prio=0 cpu=347.21ms elapsed=41.19s tid=0x00007fb0903ff000 nid=0x17b4 waiting on condition  [0x00007fb0658f6000]
"pool-1-thread-3" #13 prio=5 os_prio=0 cpu=11.74ms elapsed=41.17s tid=0x00007fb090409000 nid=0x17b5 waiting on condition  [0x00007fb0657f5000]
"pool-2-thread-1" #17 prio=5 os_prio=0 cpu=1.28ms elapsed=40.48s tid=0x00007fb090410800 nid=0x17bc waiting on condition  [0x00007fb0651d2000]
"pool-2-thread-2" #18 prio=5 os_prio=0 cpu=28.98ms elapsed=40.48s tid=0x00007fb090413800 nid=0x17bd waiting on condition  [0x00007fb064fd0000]
"pool-2-thread-3" #19 prio=5 os_prio=0 cpu=1.72ms elapsed=40.48s tid=0x00007fb090415000 nid=0x17be waiting on condition  [0x00007fb064ecf000]
"pool-3-thread-1" #20 prio=5 os_prio=0 cpu=1.06ms elapsed=40.40s tid=0x00007fb090416800 nid=0x17bf waiting on condition  [0x00007fb064dce000]
"pool-3-thread-2" #21 prio=5 os_prio=0 cpu=16.81ms elapsed=40.40s tid=0x00007fb090418800 nid=0x17c0 waiting on condition  [0x00007fb064ccd000]
"pool-3-thread-3" #22 prio=5 os_prio=0 cpu=1.78ms elapsed=40.40s tid=0x00007fb09041a800 nid=0x17c1 waiting on condition  [0x00007fb064bcc000]
"pool-4-thread-1" #24 prio=5 os_prio=0 cpu=0.89ms elapsed=40.31s tid=0x00007fb09041c800 nid=0x17c3 waiting on condition  [0x00007fb064acb000]
"pool-4-thread-2" #25 prio=5 os_prio=0 cpu=9.71ms elapsed=40.31s tid=0x00007fb09041d800 nid=0x17c4 waiting on condition  [0x00007fb0649ca000]
"pool-4-thread-3" #26 prio=5 os_prio=0 cpu=1.38ms elapsed=40.31s tid=0x00007fb09041f000 nid=0x17c5 waiting on condition  [0x00007fb0648c9000]
"pool-5-thread-1" #28 prio=5 os_prio=0 cpu=0.28ms elapsed=40.25s tid=0x00007fb090421000 nid=0x17c7 waiting on condition  [0x00007fb0647c8000]
"pool-5-thread-2" #29 prio=5 os_prio=0 cpu=13.80ms elapsed=40.25s tid=0x00007fb090422800 nid=0x17c8 waiting on condition  [0x00007fb0646c7000]
"pool-5-thread-3" #30 prio=5 os_prio=0 cpu=0.77ms elapsed=40.25s tid=0x00007fb090424800 nid=0x17c9 waiting on condition  [0x00007fb0645c6000]
```

In the full NetBeans build, that's more than 1,692 unnecessary operating system threads that are created and left waiting in the system until the Java VM terminates. The files are downloaded serially, so none of these threads provide any benefit. They serve only to create 564 separate race conditions that randomly choose among the three connection methods for each file (direct, `http_proxy`, or `https_proxy`). In fact, the threads are created even when all of the connections are direct, and there is no proxy server.

### Workaround

A partial workaround for the problem is to unset one of the proxy environment variables:

```console
$ unset https_proxy
```

In the example below, just one connection was required when using this workaround:

```
1666983888.766   6023 10.203.206.244 TCP_TUNNEL/200 21701 CONNECT netbeans.osuosl.org:443 - HIER_DIRECT/64.50.233.100 -
```

The workaround, however, doesn't solve the problem of all the unnecessary threads nor the unnecessary attempts to make direct connections when a proxy server is defined and working.
