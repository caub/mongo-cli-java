mongo-cli-java
====================

MongoDB from a websocket, with extra features:
 - **Access-rights** on documents
 - **Notifications**

It works by sending `{fn: someFunction, args: someArguments}` following the syntax of [Mongo's Java driver](http://api.mongodb.org/java/current/com/mongodb/DBCollection.html)  
Example in javascript:

    send({fn:'find', args:[{a: 2}]}, function(d){console.log(d)})
    send({fn:'save', args:[{a:3}]}, function(d){console.log(d)})
    send({fn:'find', args:[{a: {$gt:2}}]}, function(d){console.log(d)})
    send({fn:'findAndModify', args:[{a:3},{$set:{a:5,b:12}}]}, function(d){console.log(d)})
    send({fn:'findAndModify', args:[{a:6},{a:5,b:12},true, false]}, function(d){console.log(d)})
    send({fn:'remove', args:[{a: {$exists: true}}]}, function(d){console.log(d)})

    //when you're authentified, access-rights are enabled
    send({fn:'save', args:[{a:4, _canRemove:['john@gmail.com']}]}, function(d){console.log(d)})
    send({fn:'save', args:[{a:5,_canRead:['john@gmail.com'],_canUpsert:['john@gmail.com'],_canRemove:['john@gmail.com']}]}, function(d){console.log(d)})

    //notifications:
      connected clients receive the update when their rights allow it
      note: not all methods will trigger notifications  findAndModify, findAndRemove do
           update, remove won't since it just return the number of updates.

Server demos: [Heroku](http://mongo-cli-java.herokuapp.com/), [Cloudbees](http://mongo-cli-java.cyril.eu.cloudbees.net/)

Client demos: [Paint](http://jsbin.com/muquv/latest), [Chat](http://jsbin.com/dozik/latest), [Todos](http://jsbin.com/sonuw/latest)
