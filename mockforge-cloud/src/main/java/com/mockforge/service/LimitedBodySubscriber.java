package com.mockforge.service;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.io.IOException;
// Stop downloading if an upstream response exceeds the prototype limit.
public class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private long bytes;
    private boolean stopped;

    // Return the completed response body.
    public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
    }

    // Keep the subscription so the download can be cancelled.
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        delegate.onSubscribe(subscription);
    }

    // Count incoming bytes and stop if the response exceeds 32 KiB.
    public void onNext(List<ByteBuffer> buffers) {
        if (stopped) {
            return;
        }
        for (ByteBuffer buffer : buffers) {
            bytes += buffer.remaining();
        }
        if (bytes > 32768) {
            stopped = true;
            subscription.cancel();
            delegate.onError(new IOException("Upstream response exceeds 32 KiB"));
        } else {
            delegate.onNext(buffers);
        }
    }

    // Pass a download error to the body reader.
    public void onError(Throwable error) {
        if (!stopped) {
            delegate.onError(error);
        }
    }

    // Finish reading the body when the download completes.
    public void onComplete() {
        if (!stopped) {
            delegate.onComplete();
        }
    }
}
