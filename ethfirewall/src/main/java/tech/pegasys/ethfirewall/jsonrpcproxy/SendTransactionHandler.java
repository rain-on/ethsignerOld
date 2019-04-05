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

import tech.pegasys.ethfirewall.RawTransactionConverter;
import tech.pegasys.ethfirewall.jsonrpc.JsonRpcRequest;
import tech.pegasys.ethfirewall.jsonrpc.SendTransactionJsonParameters;
import tech.pegasys.ethfirewall.jsonrpc.response.JsonRpcError;
import tech.pegasys.ethfirewall.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.ethfirewall.signing.TransactionSigner;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTransactionHandler implements JsonRpcRequestHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SendTransactionHandler.class);
  private final JsonRpcErrorReporter errorReporter;
  private final HttpClient ethNodeClient;
  private final TransactionSigner signer;
  private final RawTransactionConverter converter;

  public SendTransactionHandler(
      final JsonRpcErrorReporter errorReporter,
      final HttpClient ethNodeClient,
      TransactionSigner signer,
      RawTransactionConverter converter) {
    this.errorReporter = errorReporter;
    this.ethNodeClient = ethNodeClient;
    this.signer = signer;
    this.converter = converter;
  }

  @Override
  public void handle(final HttpServerRequest httpServerRequest, final JsonRpcRequest jsonRequest) {
    final SendTransactionJsonParameters params;
    try {
      params = SendTransactionJsonParameters.from(jsonRequest);
      final SendTransactionResponseHandler sendTransactionResponseHandler =
          new SendTransactionResponseHandler(
              errorReporter,
              ethNodeClient,
              params,
              httpServerRequest,
              jsonRequest,
              signer,
              converter);
      sendTransactionResponseHandler.constructBodyAndSendRequest();

    } catch (final NumberFormatException e) {
      LOG.debug("Parsing values failed for request: {}", jsonRequest.getParams(), e);
      errorReporter.send(
          jsonRequest,
          httpServerRequest,
          new JsonRpcErrorResponse(jsonRequest.getId(), JsonRpcError.INVALID_PARAMS));

    } catch (final IllegalArgumentException e) {
      LOG.debug("JSON Deserialisation failed for request: {}", jsonRequest.getParams(), e);
      errorReporter.send(
          jsonRequest,
          httpServerRequest,
          new JsonRpcErrorResponse(jsonRequest.getId(), JsonRpcError.INVALID_PARAMS));
    }
  }
}
