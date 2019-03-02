package tech.pegasys.ethfirewall;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    description = "This command runs the EthFirewall.",
    abbreviateSynopsis = true,
    name = "ethfirewall",
    mixinStandardHelpOptions = true,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Pantheon is licensed under the Apache License 2.0")
public class EthFirewallCommand implements Runnable {

  private final Logger logger;
  private CommandLine commandLine;

  @Option(
      names = {"-p", "--password"},
      description = "Password required to access the key file.",
      arity = "1")
  private final String password = null;

  @Option(
      names = {"-k", "--keyfile"},
      description = "A file containing the key used to sign transactions.",
      arity = "1")
  private final File keyFilename = null;

  @Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO)")
  private final Level logLevel = null;

  @Option(
      names = "--web3-provider-endpoint",
      description = "The endpoint to which received requests are forwarded",
      arity = "1")
  private final String web3Endpoint = null;

  @Option(
      names = "--listen-endpoint",
      description = "The endpoint on which",
      arity = "1")
  private final String listenEndpoint = null;


  public EthFirewallCommand(Logger logger) {
    this.logger = logger;
  }

  public void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final InputStream in,
      final String... args) {
    commandLine = new CommandLine(this);

    commandLine.setCaseInsensitiveEnumValuesAllowed(true);

    commandLine.parse(args);
  }

  @Override
  public void run() {
    // set log level per CLI flags
    if (logLevel != null) {
      System.out.println("Setting logging level to " + logLevel.name());
      Configurator.setAllLevels("", logLevel);
    }

     //ensure the listen and forward addresses are not the same!

    // create TransactionSigner with Password and Keyfile

    // Create reverseProxy and pass in the TransactionSigner

  }
}
