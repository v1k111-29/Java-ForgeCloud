package com.mockforge.service;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

// Stop downloading if an upstream response exceeds the prototype limit.
public class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    private final HttpResponse.BodySubscriber<byte[]> responseBodySubscriber = HttpResponse.BodySubscribers.ofByteArray();

    private Flow.Subscription bodySubscription;

    private long receivedByteCount;

    private boolean downloadStopped;

    // Return the completed response body.
    @Override
    public CompletionStage<byte[]> getBody() {

        return responseBodySubscriber.getBody();
    }


    // Keep the subscription so the download can be cancelled.
    @Override
    public void onSubscribe(Flow.Subscription bodySubscription) {

        this.bodySubscription = bodySubscription;

        responseBodySubscriber.onSubscribe(bodySubscription);
    }


    // Count incoming bytes and stop if the response exceeds 32 KiB.
    @Override
    public void onNext(List<ByteBuffer> responseBuffers) {

        if (downloadStopped) {
            return;
        }

        for (ByteBuffer responseBuffer : responseBuffers) {
            receivedByteCount += responseBuffer.remaining();
        }

        if (receivedByteCount > 32768) {
            downloadStopped = true;

            bodySubscription.cancel();

            responseBodySubscriber.onError(new IOException("Upstream response exceeds 32 KiB"));
        } else {
            responseBodySubscriber.onNext(responseBuffers);
        }
    }


    // Pass a download error to the body reader.
    @Override
    public void onError(Throwable downloadError) {

        if (!downloadStopped) {
            responseBodySubscriber.onError(downloadError);
        }
    }


    // Finish reading the body when the download completes.
    @Override
    public void onComplete() {

        if (!downloadStopped) {
            responseBodySubscriber.onComplete();
        }
    }
}
