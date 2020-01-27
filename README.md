# webpayadmin-reports
Java client / API used to collect reconciliation information from Svea Ekonomi's Webpay web services.

To install and run
==================
Prerequisites:
* [Java >= 1.8](https://en.wikipedia.org/wiki/List_of_Java_virtual_machines)
* [Git](https://git-scm.com/)
* [Maven](http://maven.apache.org/)


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

### Compile and install

```
git clone https://github.com/sveawebpay/webpayadminreports
cd webpayadminreports
mvn -U clean compile assembly:single
```

The above will create a single jar which contains all necessary code to run this API.

You'll find the jar in the target directory, it will be name something like below

```
webpayadmin-reports-0.0.1-jar-with-dependencies.jar
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
 
 -recipientorgnr <arg>       Sets recipient org nr to <arg> in output. Used to
 						     construct the file name in some cases.
 
 -recipientname <arg>		 Sets recipient name to <arg> in output. Used to
 						     construct the file name in some cases.

 -debug <arg>                Enable debug

 
 -c,--configfile <arg>       XML-configuration file where credentials are
                             stored. Use a config file when detailed
                             configuration is needed.

 -j --jsonconfigfile <arg>   Json-configuration file where credentials and
                             other options are stored. Use a config file when
                             detailed configuration is needed.
                             The option -savejsonconfigfile can be used to 
                             generate a starting configuration file.

 -enrich                     Enrich data with as much information as possible.
 
 -noprune                    Return report type groups even if they are empty.
 							 Good to use to check what accounts are actually 
 							 checked.
                             
 -savejsonconfigfile <arg>   Save credentials as json file. Handy for generating 
 							 configuration files and listing the accounts available for
                             reporting and other actions
 

```

# Getting started with fetching information

The easiest way to create a configuration to fetch information is by first creating a config file from your credentials. 

The credentials are a username and password used for system integration. It's NOT the same as your login to https://paymentadmin.svea.com/

The username is normally your sitename as assigned by WebPay when you received your account. If you don't have these login details, contact your contact person at Svea Webpay for the credentials. 

When you have the credentials run the following

```
java -jar target/webpayadmin-reports-0.0.1-jar-with-dependencies.jar -u USERNAME -p PASSWORD -savejsonconfigfile myconfig.json
```

This will create a configuration file with your account details in the file myconfig.json.

To fetch reporting information, the next step can be as example

```
java -jar target/webpayadmin-reports-0.0.1-jar-with-dependencies.jar -j myconfig.json -d 2019-12-10 -format xlsx
```

The above example will create an Excel-report with your transactions dating to 2019-12-10.

Another example Excel Format which specifies where the file should be saved.

```
java -jar target/webpayadmin-reports-0.0.1-jar-with-dependencies.jar -u sverigetest -p sverigetest -format xlsx -outfile myfile.xlsx -outdir /tmp
```
