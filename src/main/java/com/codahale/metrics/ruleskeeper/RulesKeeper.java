package com.codahale.metrics.ruleskeeper;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import org.ruleskeeper.MetricProtoBuf.Metric;
import org.ruleskeeper.MetricProtoBuf.Metric.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

public final class RulesKeeper implements Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(RulesKeeper.class);
	private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

	public static final String RULESKEEPER_API_URI = "api/metrics/save";

	private String url;
	private String dataProviderName;
	private static Handler httpHandler = new Handler();

	public RulesKeeper(String url) {
		this(url, "Metrics");
	}

	public RulesKeeper(String url, String dataProviderName) {
		this.url = url;
		this.dataProviderName = dataProviderName;
	}

	public static final List<Integer> SUCESSS_STATUS_CODE = new ArrayList<Integer>();

	static {
		SUCESSS_STATUS_CODE.add(200);
		SUCESSS_STATUS_CODE.add(201);
		SUCESSS_STATUS_CODE.add(202);
		SUCESSS_STATUS_CODE.add(203);
		SUCESSS_STATUS_CODE.add(204);
		SUCESSS_STATUS_CODE.add(205);
		SUCESSS_STATUS_CODE.add(206);
		SUCESSS_STATUS_CODE.add(207);
		SUCESSS_STATUS_CODE.add(210);

		SUCESSS_STATUS_CODE.add(300);
		SUCESSS_STATUS_CODE.add(301);
		SUCESSS_STATUS_CODE.add(302);
		SUCESSS_STATUS_CODE.add(303);
		SUCESSS_STATUS_CODE.add(304);
		SUCESSS_STATUS_CODE.add(305);
		SUCESSS_STATUS_CODE.add(306);
		SUCESSS_STATUS_CODE.add(307);
		SUCESSS_STATUS_CODE.add(310);
	}

	public void sendSingleMetric(String hierarchyKey, String measureName, String value, long timestamp) throws IOException, InterruptedException,
			ExecutionException {

		Metric m = Metric.newBuilder().setDataProvider(dataProviderName).setHierarchyKey(hierarchyKey).setTechnicalKey(hierarchyKey).setShortName(measureName)
				.setEventDate(String.valueOf(timestamp)).setUnit(UnitType.COUNTER).setValue(value).build();
		sendMetricToServer(m, httpHandler);
	}

	public void send(String hierarchyKey, String measureName, String value, long timestamp) throws IOException, InterruptedException, ExecutionException {

		Metric m = Metric.newBuilder().setDataProvider(dataProviderName).setHierarchyKey(hierarchyKey)
				.setTechnicalKey(MetricRegistry.name(hierarchyKey, measureName)).setShortName(measureName).setEventDate(String.valueOf(timestamp))
				.setUnit(UnitType.COUNTER).setValue(value).build();
		sendMetricToServer(m, httpHandler);
	}

	public void sendMetricToServer(Metric m, AsyncCompletionHandler<Response> handler) throws IOException, InterruptedException, ExecutionException {
		ByteString data = m.toByteString();
		AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
		String dataAsString = data.toStringUtf8();
		Response r = asyncHttpClient.preparePost(url + RULESKEEPER_API_URI).addParameter("data", dataAsString).execute(handler).get();
		if (SUCESSS_STATUS_CODE.contains(r.getStatusCode())) {
			LOGGER.debug("Metric sent to RulesKeeper Server: {}", r.getStatusCode());
		} else {
			LOGGER.debug("Not able to send Metric to RulesKeeper Server: {}", r.getStatusCode());
		}
		asyncHttpClient.close();
	}

	@Override
	public void close() throws IOException {
		// nothing to do
	}

	protected String sanitize(String s) {
		return WHITESPACE.matcher(s).replaceAll("-");
	}

	public static class Handler extends AsyncCompletionHandler<Response> {
		@Override
		public Response onCompleted(Response response) {
			LOGGER.debug("" + response.getStatusCode());
			return response;
		}

		@Override
		public void onThrowable(Throwable t) {
			LOGGER.error(t.getMessage());
		}
	}

}
