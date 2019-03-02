package tech.pegasys.ethfirewall;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.utils.Numeric;

public class TransactionSigner {

  private Credentials credentials;

  public TransactionSigner(final Credentials credentials) {
    this.credentials = credentials;
  }

  public static TransactionSigner createFrom(final File keyFile, final String password)
      throws IOException, CipherException {
    Credentials credentials = WalletUtils.loadCredentials(password, keyFile);

    return new TransactionSigner(credentials);
  }

  public String signTransaction(final Transaction transaction) {
    final RawTransaction rawObj = RawTransaction.createTransaction(
        new BigInteger(transaction.getNonce()),
        new BigInteger(transaction.getGasPrice()),
        new BigInteger(transaction.getGas()),
        transaction.getTo(),
        new BigInteger(transaction.getValue()),
        transaction.getData());

    final byte[] signedMessage = TransactionEncoder.signMessage(rawObj, credentials);
    return Numeric.toHexString(signedMessage);
  }

}
