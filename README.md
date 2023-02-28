# FileAdmin - Server

FileAdmin is a web-based, mobile-first file manager.
This project only contains server code.
For more information please have a look at the corresponding web client project [fileadmin-web](https://github.com/franzmandl/fileadmin-web).

## Run in Development Mode

Use the IntelliJ run configuration *ServerKt development,jail3* or run:

```shell
spring_profiles_active=development,jail3 ./gradlew run
```

Listens on port [8485](http://localhost:8485/).

## Test

```shell
./gradlew check
```

## Build for Production

```shell
./gradlew installDist
```

## Deploy

```shell
mkdir -p /opt/fileadmin/server
rsync -aiv --delete ./build/install/fileadmin-server/ /opt/fileadmin/server
```

## Run in Production Mode

Create a file `/opt/fileadmin/config/application-production.properties` and configure it to your desires.
See `./src/main/resources/application.properties` for available properties.
At least specify `application.paths.jail`.

```shell
cd /opt/fileadmin
spring_profiles_active=production /opt/fileadmin/server/bin/fileadmin-server
```
