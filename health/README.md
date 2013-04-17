acteur-timetracker
==================

A generic JSON REST web api for keeping track of little blobs of time and 
associating ad-hoc data with them.  In addition to being useful, it serves as 
a demo of the [acteur](../../acteur) Netty-based server framework, 
and [giulius](../../giulius) for simple Guice injection.

Prerequisites
-------------

You need [MongoDB](http://mongodb.org) installed, or running somewhere accessible, to run this project.
By default it will try to talk to MongoDB on ``localhost:27017``.  If it is somewhere else, create
a file ``timetracker.properties`` in the process working dir, current user's home directory or `/etc/` with
that name, and include in it and set the properties ``mongoHost`` and ``mongoPort`` appropriately.

Build and Run
-------------

The project can be built and run using Maven.  To build it as a standalone JAR file, run

    mvn clean install assembly:assembly

which will result in a server JAR file ``target/timetracker-1.0-standalone.jar`` which you
can simply run with ``java -jar timetracker-1.0-standalone.jar``.

Web API
-------

The JSON API is semi self-documenting (as in, it could use detailed documentation, but this is
what there is) - start the server and navigate to 
[localhost:7739/help?html=true](http://localhost:7739/help?html=true), which gives you documentation
of the API.

Some of the basics:

  * **/users/$NEW_USER_ID/signup?displayName=DISPLAY+NAME** - new user signup - the HTTP body should be the password
  * **/users/$USER_ID/time/$SERIES_ID?start=$MILLIS_SINCE_1970_START&end=MILLIS_SINCE_1970_END** - PUT a new time event.  Ad-hoc properties can be added to the record, simply by including them as URL parameters
  * **/users/$USER_ID/time/$SERIES_ID?start=$MILLIS_SINCE_1970_START** - query time events.  Query parameters can be ``start``, ``end``, ``dur`` (duration) or any ad-hoc property that was passed when the record was created

SERIES_ID refers to a collection of time events that are related somehow - you create one by adding items.

Database Schema
---------------

MongoDB is schemaless, and we don't force any more than necessary.  Collections are created per-user:series, named
username_series, and looked up to satisfy requests.

