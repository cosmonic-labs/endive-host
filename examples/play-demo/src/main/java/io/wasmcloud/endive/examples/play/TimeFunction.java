package io.wasmcloud.endive.examples.play;

import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.time.Instant;

public class TimeFunction {
    public Result handle(Http.Request request) {
        return Results.ok(Instant.now().toString() + "\n").as("text/plain");
    }
}
