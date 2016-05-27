# Load Test Scenarios for Magento 1 and Magento 2

These scenarios were used to perform load tests for Magento 1 and Magento 2 applications in different environments.
Results are publicly available on [MageCore blog](https://www.magecore.com/blog):

- [Magento CE 1.9 vs Magento CE 2.0 Performance Comparison](https://www.magecore.com/blog/news/magento-ce-1-9-vs-magento-ce-2-0-performance-comparison)
- [How Does PHP 7 Affects Performance of Magento 1.9 CE vs. Magento 2.0 CE](https://www.magecore.com/blog/news/php-7-affects-performance-magento-1-9-ce-vs-magento-2-0-ce)

## Repository Structure

Tests are grouped in application folders which include following assets:
- ``media`` - image files used for catalog products
- ``media.sh`` - script that initializes application media based on catalog and images from media folder
- ``data.sql.gz`` - database dump
- ``gatling`` - load test scenarios for [Gatling](http://gatling.io/) load testing framework

## Environment Configuration

- OS: Amazon Linux AMI 2015.03
- Web server: nginx/1.8.0 + php-fpm
- PHP: 5.5.30 or 7.0.3
- Varnish: 3.0.5
- Redis: 3.0.5
- Database: MySQL 5.6.27

## Setup instructions

- Create database and import dump from ``data.sql.gz`` of proper application
- Install Magento application using database created on the previous step
- Copy ``media`` directory, ``media.set`` and ``media.sh`` script to some directory
- Run ``media.sh /path/to/magento_pub /path/to/media.set /path/to/source_media`` to initialize product images
- [Install](http://gatling.io/docs/2.2.0/quickstart.html#installing) Gatling
- Copy files from `magento1/gatling` and `magento2/gatling` directories to gatling `user-files` directory
- Run gatling scenario using following options (please use m1 or m2 instead mX in the parameters bellow):

| Option | Description | Default Value |
| --- | --- | --- |
| dataDir | Data directory used in test scenarios | mXce |
| users | The number of concurrent users | 20 |
| ramp | Increase load to number of users in, sec | 30 |
| during | Run test during period, minutes | 10 |
| domain | Testing domain name | magento.test.com |
| useSecure | Use HTTPS for secure pages | 0 |
| project | Project Name for Report | Magento |

```console
$ JAVA_OPTS="-Ddomain=www.mXce.com -Dusers=10"
$ gatling -s mX.defaultFrontTest
```

## Magento1 Pre Test Setup

1. Install Phoenix_VarnishCache extension
2. Install Oro_Ajax module from ``magento1/ajax`` directory

## Magento2 Pre Test Setup

1. Page load time optimization: build JS, enable JS minification, enable CSS minification
2. Deploy static content: ``php bin/magento setup:static-content:deploy``
3. Run compiler: ``php bin/magento setup:di:compile``
4. Enable production mode: ``Set $MAGE_MODE production`` 
5. Reindex catalog: ``php bin/magento indexer:reindex``
6. Clean cache: ``php bin/magento cache:clean``

