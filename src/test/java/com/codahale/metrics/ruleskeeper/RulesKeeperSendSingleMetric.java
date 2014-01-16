package com.codahale.metrics.ruleskeeper;

import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class RulesKeeperSendSingleMetric implements Runnable {

	final RulesKeeper rk = new RulesKeeper("http://127.0.0.1:9000/");

	public static AtomicLong counter = new AtomicLong(0);

	private final Timer timer = RulesKeeperTest.metricsRegistry.timer(MetricRegistry.name(RulesKeeperSendSingleMetric.class, "run"));

	@Override
	public void run() {
		final Timer.Context timerContext = timer.time();

		System.out.println(RulesKeeperSendSingleMetric.counter.incrementAndGet());
		long timestamp = Calendar.getInstance().getTimeInMillis();
		try {
			rk.sendSingleMetric("org.ruleskeeper", "org.ruleskeeper.sendSingleMetric", "" + (Math.random() * 1000), timestamp);
		} catch (IOException e) {

			e.printStackTrace();
		} catch (InterruptedException e) {

			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		timerContext.close();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
