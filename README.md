# Load Test Scenarios for Magento 1 and Magento 2

These test scenarios are more proper version of performance benchmark than the one that was performed by MageCore Inc.

You can find original test scenarios at the following repository with the links to original research:
[https://github.com/magecorelab/magento-load-test](https://github.com/magecorelab/magento-load-test)

## Repository Structure

This reposistory contains only simulations and data source for load tests, not actual Magento instances.

## Environment Configuration

[Byte Hypernode GO BIG XL](https://www.byte.nl/hosting/magento/prijzen)

* OS: Ubuntu 12.04.5 LTS
* Web server: nginx/1.9.7
* PHP: 7.0.6 (over FPM)
* Varnish: 4.0.3
* Redis: 2.8.9
* Database: Percona Server 5.6.22-71.0-log
* RAM: 16GB
* CPU: 8

InnoDB configurations:

```
[mysqld]
innodb_buffer_pool_size = 8G
```

## Setup Instructions Magento 1.x Server

1. Clone repository with [Magento 1.x setup bootstrap](https://github.com/IvanChepurnyi/load-test-magento1-bootstrap) into your Hypernode:
```console
git clone https://github.com/IvanChepurnyi/load-test-magento1-bootstrap.git ./magento1
```

2. Install Magento 1.9.2.4 via `setup.sh` script

```console
magento1/setup.sh database_name magento-domain-name.com [database-type] [version]
```

* `database-type` is a type of database (large, original). Optional. By default it will use one with more configurables.
* `version` Magento 1.x version number. Optional. By default it will use `1.9.2.4`.


## Setup Instructions Magento 2.x Server


1. Clone repository with [Magento 2.x setup bootstrap](https://github.com/IvanChepurnyi/load-test-magento2-bootstrap) into your Hypernode:
```console
git clone https://github.com/IvanChepurnyi/load-test-magento2-bootstrap.git ./magento2
```

2. Install desired version of Magento 2.x via `setup.sh` script

```console
magento2/setup.sh database_name magento-domain-name.com [version] [database-type]
```

* `database-type` is a type of database (large, original). Optional. By default it will use one with more configurables.
* `version` Magento 2.x version number. Optional. By default it will use `latest` (currently 2.1.0).

## Running Tests

1. Copy contents from `gatling` directory in this repository into your gatling `user-files` directory
```console
cp -r ./gatling/* [path-to-gatling]/user-files/
```

2. Run load test sessions with the following commands:

```console
cd [path-to-gatling]/

# Magento 1 Default Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -Dusers=10"
gatling -s m1.defaultFrontTest

# Magento 1 Large Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DsimpleProductCsv=product_simple_large -Dusers=10"
gatling -s m1.defaultFrontTest

# Magento 1 Original Oro Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DsimpleProductCsv=product_simple_large -Dusers=10"
gatling -s m1.defaultFrontTest

# Magento 2.0.7 Default Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DmagentoVersion=2.0.7 -Dusers=10"
gatling -s m2.defaultFrontTest

# Magento 2.0.7 Large Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DmagentoVersion=2.0.7 -DsimpleProductCsv=product_simple_large -Dusers=10"
gatling -s m2.defaultFrontTest

# Magento 2.0.7 Original Oro Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DmagentoVersion=2.0.7 -DsimpleProductCsv=product_simple_large -Dusers=10"
gatling -s m2.defaultFrontTest

# Magento 2.1.0 Default Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DmagentoVersion=2.1.0 -Dusers=10"
gatling -s m2.defaultFrontTest

# Magento 2.1.0 Large Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DmagentoVersion=2.1.0 -DsimpleProductCsv=product_simple_large -Dusers=10"
gatling -s m2.defaultFrontTest

# Magento 2.1.0 Original Oro Database
JAVA_OPTS="-Ddomain=magento-domain-name.com -DmagentoVersion=2.1.0 -DsimpleProductCsv=product_simple_large -Dusers=10"
gatling -s m2.defaultFrontTest
```

### Test Parameters Applicable for both load tests
| Option | Description | Default Value |
| --- | --- | --- |
| dataDir | Data directory used in test scenarios | mXce |
| users | The number of concurrent users | 20 |
| ramp | Increase load to number of users in, sec | 30 |
| during | Run test during period, minutes | 10 |
| domain | Testing domain name | magento.test.com |
| useSecure | Use HTTPS for secure pages | 0 |
| project | Project Name for Report | Magento |
| simpleProductCsv | CSV File name (without suffix) | simple_product |

### Magento 2.x Specific parameters
| Option | Description | Default Value |
| --- | --- | --- |
| magentoVersion | Magento 2.x Version under test | 2.0.7 |

