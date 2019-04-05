/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.ethfirewall.jsonrpcproxy;

import static java.util.Collections.singletonList;

import tech.pegasys.ethfirewall.RawTransactionConverter;
import tech.pegasys.ethfirewall.jsonrpc.JsonRpcRequest;
import tech.pegasys.ethfirewall.jsonrpc.SendTransactionJsonParameters;
import tech.pegasys.ethfirewall.jsonrpc.response.JsonRpcError;
import tech.pegasys.ethfirewall.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.ethfirewall.signing.TransactionSigner;

import java.io.IOException;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.RawTransaction;

public class SendTransactionResponseHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SendTransactionResponseHandler.class);
  private static final String JSON_RPC_VERSION = "2.0";
  private static final String JSON_RPC_METHOD = "eth_sendRawTransaction";

  private final JsonRpcErrorReporter errorReporter;
  private final HttpServerRequest httpServerRequest;
  private final JsonRpcRequest jsonRequest;
  private final HttpClient ethNodeClient;
  private final SendTransactionJsonParameters params;
  private final TransactionSigner signer;
  private final RawTransactionConverter converter;

  SendTransactionResponseHandler(
      final JsonRpcErrorReporter errorReporter,
      final HttpClient ethNodeClient,
      final SendTransactionJsonParameters params,
      final HttpServerRequest httpServerRequest,
      final JsonRpcRequest jsonRequest,
      final TransactionSigner signer,
      RawTransactionConverter converter) {
    this.errorReporter = errorReporter;
    this.ethNodeClient = ethNodeClient;
    this.params = params;
    this.httpServerRequest = httpServerRequest;
    this.jsonRequest = jsonRequest;
    this.signer = signer;
    this.converter = converter;
  }

  void constructBodyAndSendRequest() {
    if (!params.sender().equalsIgnoreCase(signer.getAddress())) {
      LOG.info("From address does not match unlocked account");
      errorReporter.send(
          jsonRequest,
          httpServerRequest,
          new JsonRpcErrorResponse(jsonRequest.getId(), JsonRpcError.INVALID_PARAMS));
      return;
    }

    final String signedTransactionHexString;
    try {
      final RawTransaction rawTransaction = converter.from(params); // this will populate defaults
      signedTransactionHexString = signer.signTransaction(rawTransaction);

    } catch (final IllegalArgumentException e) {
      LOG.debug("Bad input value from request: {}", jsonRequest, e);
      errorReporter.send(
          jsonRequest,
          httpServerRequest,
          new JsonRpcErrorResponse(jsonRequest.getId(), JsonRpcError.INVALID_PARAMS));
      return;
    } catch (final IOException e) {
      LOG.debug("Unable to determine nonce for sendTransaction: {}", jsonRequest, e);
      errorReporter.send(
          jsonRequest,
          httpServerRequest,
          new JsonRpcErrorResponse(jsonRequest.getId(), JsonRpcError.INTERNAL_ERROR));
      return;
    } catch (final Throwable e) {
      LOG.debug("Unhandled error processing request: {}", jsonRequest, e);
      errorReporter.send(
          jsonRequest,
          httpServerRequest,
          new JsonRpcErrorResponse(jsonRequest.getId(), JsonRpcError.INTERNAL_ERROR));
      return;
    }

    final JsonRpcRequest sendRawTransaction =
        new JsonRpcRequest(
            JSON_RPC_VERSION, JSON_RPC_METHOD, singletonList(signedTransactionHexString));
    sendRawTransaction.setId(jsonRequest.getId());

    final HttpClientRequest proxyRequest =
        ethNodeClient.request(httpServerRequest.method(), httpServerRequest.uri(), this::handle);

    proxyRequest.headers().setAll(httpServerRequest.headers());
    proxyRequest.headers().remove("Content-Length"); // created during 'end'.
    proxyRequest.setChunked(false);
    proxyRequest.end(Json.encodeToBuffer(sendRawTransaction));
  }

  void handle(final HttpClientResponse response) {
    logResponse(response);
    // If a bad request, check if its a NONCE_TOO_LOW issue.
    if (response.statusCode() == HttpResponseStatus.BAD_REQUEST.code()) {
      response.bodyHandler(body -> handleJsonFailure(body, response));
    } else {
      response.bodyHandler(body -> feedbackProxyResponse(body, response));
    }
  }

  private void handleJsonFailure(final Buffer body, final HttpClientResponse response) {
    final JsonObject jsonBody = new JsonObject(body);
    // If the nonce did not come from the initial request, and it is too low, resend with
    // a new nonce.
    if (isLowNonceError(jsonBody) && !params.nonce().isPresent()) {
      constructBodyAndSendRequest();
    } else {
      feedbackProxyResponse(body, response);
    }
  }

  private void feedbackProxyResponse(final Buffer body, final HttpClientResponse response) {
    httpServerRequest.response().setStatusCode(response.statusCode());
    httpServerRequest.response().headers().setAll(response.headers());
    httpServerRequest.response().setChunked(false);
    httpServerRequest.response().end(body);
  }

  private boolean isLowNonceError(final JsonObject jsonResponseBody) {
    if (jsonResponseBody.containsKey("error") && jsonResponseBody.getValue("error") != null) {
      JsonObject errorContent = jsonResponseBody.getJsonObject("error");
      if (errorContent.getInteger("code").equals(JsonRpcError.NONCE_TOO_LOW.getCode())) {
        return true;
      }
    }
    return false;
  }

  private void logResponse(final HttpClientResponse response) {
    LOG.debug("Response status: {}", response.statusCode());
  }

  private void logResponseBody(final Buffer body) {
    LOG.debug("Response body: {}", body);
  }

  private void logRequest(
      final JsonRpcRequest originalJsonRpcRequest,
      final HttpServerRequest originalRequest,
      final HttpClientRequest proxyRequest,
      final Buffer proxyRequestBody) {
    LOG.debug(
        "Original method: {}, uri: {}, body: {}, Proxy: method: {}, uri: {}, body: {}",
        originalRequest.method(),
        originalRequest.absoluteURI(),
        Json.encodePrettily(originalJsonRpcRequest),
        proxyRequest.method(),
        proxyRequest.absoluteURI(),
        proxyRequestBody);
  }
}
