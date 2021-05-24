package org.folio.edge.ltiCourses;

import java.io.File;
import java.io.FileOutputStream;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.HttpHeaders;

import org.apache.log4j.Logger;
import org.folio.edge.ltiCourses.cache.BoxFileCache;

import com.box.sdk.BoxTransactionalAPIConnection;
import com.box.sdk.BoxFile;

public class BoxDownloadHandler {
  protected BoxTransactionalAPIConnection api;

  private static final Logger logger = Logger.getLogger(BoxDownloadHandler.class);

  public BoxDownloadHandler(String appToken) {
    if (appToken == null || appToken.isEmpty()) {
      logger.info("No Box API App Token was provided, Box URLs will not be transformed.");
    } else {
      logger.info("Creating new Box API instance using App Token starting with: " + appToken.substring(0, 5));
      api = new BoxTransactionalAPIConnection(appToken);
    }
  }

  protected void handleDownloadRequest(RoutingContext ctx) {
    if (api == null) {
      logger.error("No Box API App Token was provided, Box URLs cannot be transformed.");
      ctx.response()
        .setStatusCode(400)
        .end("Direct download of this file is not supported by the system.");

      return;
    }

    final String hash = ctx.request().getParam("hash");
    logger.info("Handling request to download Box file hashed at: " + hash);

    String boxFileId = BoxFileCache.getInstance().get(hash);

    if (boxFileId == null || boxFileId.isEmpty()) {
      ctx.response()
        .setStatusCode(400)
        .end("This file is no longer available for download. Reload the list of reserves and try again.");

      return;
    }

    BoxFile file;
    File tempFile;

    try {
      file = new BoxFile(api, boxFileId);

      logger.info("Creating temp file and setting up output stream...");
      tempFile = File.createTempFile("edge-lti-box-file-", null);
      tempFile.deleteOnExit();
      FileOutputStream stream = new FileOutputStream(tempFile.getPath());

      logger.info("Downloading Box file to temp file...");
      file.download(stream);
      stream.close();
    } catch (Exception e) {
      logger.error("Failed while downloading Box file: " + e.getMessage());
      ctx.response()
        .setStatusCode(500)
        .end(e.toString());

      return;
    }

    logger.info("Sending the Box file that was temporarily stored at " + tempFile.getPath());

    ctx.response()
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
      .putHeader("Content-Disposition", "attachment; filename=\"" + file.getInfo().getName() + "\"")
      .putHeader(HttpHeaders.TRANSFER_ENCODING, "chunked")
      .sendFile(tempFile.getPath());

    tempFile.delete();
  }
}