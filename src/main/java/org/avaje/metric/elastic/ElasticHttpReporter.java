package org.avaje.metric.elastic;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.avaje.metric.report.MetricReporter;
import org.avaje.metric.report.ReportMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.net.ConnectException;
import java.time.LocalDate;

/**
 * Http(s) based Reporter that sends JSON formatted metrics directly to Elastic.
 */
public class ElasticHttpReporter implements MetricReporter {

  private static final Logger logger = LoggerFactory.getLogger(ElasticHttpReporter.class);

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final OkHttpClient client = new OkHttpClient();

  private final String bulkUrl;

  private final ElasticReporterConfig config;

  public ElasticHttpReporter(ElasticReporterConfig config) {
    this.config = config;
    this.bulkUrl = config.getUrl() + "/_bulk";

    // put the template to elastic if it is not already there
    new TemplateApply(client, config.getUrl(), config.getTemplateName()).run();
  }

  /**
   * Send the non-empty metrics that were collected to the remote repository.
   */
  @Override
  public void report(ReportMetrics reportMetrics) {

    String today = today();
    String json = null;
    try {
      StringWriter writer = new StringWriter(1000);
      BulkJsonWriteVisitor jsonVisitor = new BulkJsonWriteVisitor(writer, reportMetrics, config, today);
      jsonVisitor.write();

      json = writer.toString();

      if (logger.isTraceEnabled()) {
        logger.trace("Sending:\n{}", json);
      }

      RequestBody body = RequestBody.create(JSON, json);
      Request request = new Request.Builder()
          .url(bulkUrl)
          .post(body)
          .build();

      Response response = client.newCall(request).execute();
      if (!response.isSuccessful()) {
        logger.warn("Unsuccessful sending metrics payload to server - {}", response.body().string());
        storeJsonForResend(json);
      } else if (logger.isTraceEnabled()) {
        logger.trace("Bulk Response - {}", response.body().string());
      }

    } catch (ConnectException e) {
      logger.info("Connection error sending metrics to server: " + e.getMessage());
      storeJsonForResend(json);

    } catch (Exception e) {
      logger.error("Unexpected error sending metrics to server", e);
      storeJsonForResend(json);
    }
  }

  private String today() {
    return LocalDate.now().toString();
  }

  protected void storeJsonForResend(String json) {
    // override this to support store and re-send 
  }


  @Override
  public void cleanup() {
    // Do nothing
  }

}
