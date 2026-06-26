package io.wasmcloud.endive.examples.play;

import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

public class HelloFunction {
    public Result handle(Http.Request request) {
        return Results.ok("hello from java\n").as("text/plain");
    }
}
