# webpayadmin-reports
Java client / API used to collect reconciliation information from Svea Ekonomi's Webpay web services.

To install and run
==================
Prerequisites:
* Java 1.8
* Maven
* Git
* (Ant)

### Download and install dependencies

The below dependencies are planned to be in maven central. Before they are deployed there you need to install them manually in your local maven repository. Simple instructions for that below.

```
git clone https://github.com/sveawebpay/webpay-common
cd webpay-common
mvn install
cd ..
git clone https://github.com/sveawebpay/webpay-report-converters
cd webpay-report-converters
mvn install
cd ..
git clone https://github.com/sveawebpay/webpayadminservice-client
cd webpayadminservice-client
mvn install
cd ..
```

### Install

```
git clone https://github.com/sveawebpay/webpayadminreports
cd webpayadminreports
mvn -U clean compile assembly:single
java -jar target/webpayadmin-reports-0.0.1-SNAPSHOT-jar-with-dependencies.jar -u USERNAME -p PASSWORD
```


If you end up with "Invalid target release", when compiling, see http://roufid.com/invalid-target-release-in-maven-build/

Usage
=====
The purpose of this software is to get reconcilation reports from Svea Ekonomi in a composite format. This means that you can get information about all payments during a given time span into one single file.

The services used to compile the information are described [here](https://www.svea.com/se/sv/foretag/betallosningar/betallosningar-for-e-handel/tech-site/?currentTab=custom-integration).

When username and password is used (which is supplied by Svea Ekonomi) you'll get a report of all accounts that are tied to that username.

To control more in detail what accounts are fetched, a configuration file can be used.

```
usage: WebpayAdminClientMain
 -u,--user <arg>             User supplied by Svea Ekonomi to fetch
                             reports. Can be specified in config-file.

 -p,--pass <arg>             Password supplied by Svea Ekonomi to fetch
                             reports. Can be specified in config-file.

 -a,--account <arg>          Specify account when using user as argument.
                             Not mandatory

 -t,--type <arg>             Specify type of account. Mandatory when
                             account is used.

 -d,--fromdate <arg>         From date in format yyyy-MM-dd. If omitted,
                             yesterday's date is used
                             
 -untildate <arg>            Until date in format yyyy-MM-dd
                             
 -format <arg>               Select other format than json. Available
                             formats are 'xlsx', 'csv', 'flat-json' and 'bgmax'
                             
 -outdir <arg>               Output to directory (and use outfile name if
                             present)
                             
 -outfile <arg>              Output to file instead of stdout
 
 -recipientorgnr <arg>       Sets recipient org nr to this in output

 -debug <arg>                Enable debug

 
 -c,--configfile <arg>       Configuration file where credentials are
                             stored. Use a config file when detailed
                             configuration is needed.

 -enrich							Enrich data with as much information as possible.
 
 -noprune							Return report type groups even if they are empty.
 									Good to use to check what accounts are actually 
 									checked.
                             
 -savejsonconfigfile <arg>   Save credentials as json file. Handy for generating 
 							 configuration files.
 

```

The config file contains your credentials that you get as a client of Svea.

A template config file is supplied in this code to show how the config file must look like.

Example of configuration file is found in <pre>src/main/resources/config-example.xml</pre>

Example with configuration file

```
java -jar target/webpayadmin-reports-0.0.1-SNAPSHOT-jar-with-dependencies.jar -c config.xml 
```

Example Excel Format
```
java -jar target/webpayadmin-reports-0.0.1-SNAPSHOT-jar-with-dependencies.jar -u sverigetest -p sverigetest -format xlsx -outfile myfile.xlsx -outdir /tmp
```
