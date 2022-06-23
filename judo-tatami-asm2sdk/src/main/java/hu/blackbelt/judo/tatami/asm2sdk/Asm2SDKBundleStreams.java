package hu.blackbelt.judo.tatami.asm2sdk;

import java.io.InputStream;

public class Asm2SDKBundleStreams {

	private final InputStream sdkBundleStream;
	private final InputStream internalBundleStream;
	private final InputStream guiceBundleStream;
	private final InputStream springBundleStream;

	public Asm2SDKBundleStreams(InputStream sdkBundleStream, InputStream internalBundleStream,
								InputStream guiceBundleStream, InputStream springBundleStream) {
		this.sdkBundleStream = sdkBundleStream;
		this.internalBundleStream = internalBundleStream;
		this.guiceBundleStream = guiceBundleStream;
		this.springBundleStream = springBundleStream;
	}

	public InputStream getSdkBundleStream() {
		return sdkBundleStream;
	}

	public InputStream getInternalBundleStream() {
		return internalBundleStream;
	}

	public InputStream getGuiceBundleStream() {
		return guiceBundleStream;
	}

	public InputStream getSpringBundleStream() {
		return springBundleStream;
	}

}
