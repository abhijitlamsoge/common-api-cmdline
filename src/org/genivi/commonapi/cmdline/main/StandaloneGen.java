package org.genivi.commonapi.cmdline.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.eclipse.xtext.generator.IFileSystemAccess;
import org.franca.core.dsl.FrancaIDLStandaloneSetup;
import org.franca.core.dsl.FrancaIDLVersion;
import org.franca.core.dsl.FrancaPersistenceManager;
import org.franca.core.franca.FModel;
import org.franca.core.franca.FModelElement;
import org.franca.deploymodel.core.FDModelExtender;
import org.franca.deploymodel.dsl.FDeployPersistenceManager;
import org.franca.deploymodel.dsl.FDeployStandaloneSetup;
import org.franca.deploymodel.dsl.fDeploy.FDInterface;
import org.franca.deploymodel.dsl.fDeploy.FDModel;
import org.genivi.commonapi.cmdline.GeneratorInterface;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Runner for any standalone generator/transformation from Franca files.
 * 
 * @author Klaus Birken (itemis)
 * @author Jacques Guillou (pelagicore)
 */
public class StandaloneGen {

	// prepare class for logging....
	private static final Logger logger = Logger.getLogger(StandaloneGen.class);

	private static final String TOOL_VERSION = "0.3.0";
	private static final String FIDL_VERSION = FrancaIDLVersion.getMajor()
			+ "." + FrancaIDLVersion.getMinor();

	private static final String HELP = "h";
	private static final String FIDLFILE = "f";
	private static final String OUTDIR = "o";
	private static final String GENERATORS_PATH = "g";

	private static final String VERSIONSTR = "FrancaStandaloneGen "
			+ TOOL_VERSION + " (Franca IDL version " + FIDL_VERSION + ").";

	private static final String HELPSTR = "commonAPICodeGen CodeGeneratorIDs [OPTIONS]\nCodeGeneratorID can be one of the following : \n\t- api\n\t- someip\n\t- qt\n\t- dbus";
	private static final String EXAMPLE_HELPSTR = "\ncommonAPICodeGen -f MyDeploymentFile-dbus.fdepl -o /tmp/gen core dbus [OPTIONS]\nCodeGeneratorID can be one of the following : \n\t- api\n\t- someip\n\t- api\n\t- qt\n\t- dbus";

	private static Injector injector;

	private static final String GENERATOR_CLASS_PREFIX = "org.genivi.commonapi.";
	private static final String GENERATOR_CLASS_SUFFIX = ".Generator";

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		injector = new FrancaIDLStandaloneSetup()
				.createInjectorAndDoEMFRegistration();
		new FDeployStandaloneSetup().createInjectorAndDoEMFRegistration();

		int retval = injector.getInstance(StandaloneGen.class).run(args);
		if (retval != 0)
			System.exit(retval);
	}

	void printHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(HELPSTR, options);
		formatter.printHelp(EXAMPLE_HELPSTR, options);
	}

	// injected fragments
	@Inject
	FrancaPersistenceManager fidlLoader;

	@Inject
	FDeployPersistenceManager fdeploymentModelLoader;

	ArrayList<File> m_writtenFiles = new ArrayList<File>();

	ArrayList<GeneratorInterface> m_generators = new ArrayList<GeneratorInterface>();

    private FModel getModel(FModelElement fModelElement) {
        if (fModelElement.eContainer() instanceof FModel)
            return (FModel) fModelElement.eContainer();
        return getModel((FModelElement)(fModelElement.eContainer()));
    }

	public int run(String[] args) throws Exception {

		Options options = getOptions();

		// print version string
		System.out.println(VERSIONSTR);

		// create the parser
		CommandLineParser parser = new GnuParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (final ParseException exp) {
			logger.error(exp.getMessage());

			printHelp(options);
			return -1;
		}

		if (line.hasOption(HELP) || checkCommandLineValues(line) == false) {
			printHelp(options);
			return -1;
		}

		String fidlFile = line.getOptionValue(FIDLFILE);
		File file = new File(fidlFile);

		// call generator and save files
		final String outputFolder = line.getOptionValue(OUTDIR);

		FDModel fdeploymentModel = fdeploymentModelLoader.loadModel(
				file.getName(), file.getAbsoluteFile().getParentFile()
						.getAbsolutePath());
		if (fdeploymentModel == null) {
			logger.error("Couldn't load Franca deployment file '" + fidlFile
					+ "'.");
			return -1;
		}
		FDModelExtender fModelExtender = new FDModelExtender(fdeploymentModel);
		if (fModelExtender.getFDInterfaces().size() <= 0)
			logger.error("No Interfaces were deployed, nothing to generate.");

		FModel fModel = getModel(fModelExtender.getFDInterfaces().get(0).getTarget());

		List<FDInterface> fInterfaces = fModelExtender.getFDInterfaces();

		IFileSystemAccess f = new IFileSystemAccess() {

			@Override
			public void generateFile(String arg0, String arg1, CharSequence arg2) {
				// TODO
				logger.error("Not implemented");
				throw new RuntimeException();
			}

			@Override
			public void generateFile(String path, CharSequence content) {
				File outputFile = new File(outputFolder + File.separator + path);

				// logger.info("Content of file : " + content);

				if (m_writtenFiles.contains(outputFile)) {
					logger.info("Skipping already written file : "
							+ outputFile.getAbsolutePath());
					logger.info("Content of skipped file : " + content);
					// logger.info("Content of already generated file : " +
					// content);
					return;
				}

				// m_writtenFiles.add(outputFile);

				try {
					outputFile.getParentFile().mkdirs();

					byte[] bytesToWrite = content.toString().getBytes();

					try {
						FileInputStream fis = new FileInputStream(outputFile);
						byte[] existingFileContent = new byte[fis.available()];
						fis.read(existingFileContent);
						fis.close();
						logger.info("length " + existingFileContent.length
								+ " " + bytesToWrite.length);
						if (Arrays.equals(bytesToWrite, existingFileContent)) {
							logger.info("No change to file "
									+ outputFile.getAbsolutePath());
							return;
						}

					} catch (Exception e) {
						// e.printStackTrace();
					}

					logger.info("Writing file " + outputFile.getAbsolutePath());

					outputFile.createNewFile();
					FileOutputStream os = new FileOutputStream(outputFile);

					os.write(bytesToWrite);
					os.close();

				} catch (IOException e) {
					logger.error("Can not create file "
							+ outputFile.getAbsolutePath());
					e.printStackTrace();
				}

			}

			@Override
			public void deleteFile(String arg0) {
				// TODO
				logger.error("Not implemented");
				throw new RuntimeException();
			}

		};

		String generatorsPath = line.getOptionValue(GENERATORS_PATH);
		if (generatorsPath.equals(null))
			generatorsPath = ".";
		// File localFolder = new File(".");

		ArrayList<URL> urls = new ArrayList<URL>();
		for (File jarFile : new File(generatorsPath).listFiles()) {
			if (jarFile.getName().endsWith(".jar")) {
				urls.add(jarFile.toURI().toURL());
				logger.trace("Found generator JAR : " + jarFile);
			}
		}

		URL[] urlArray = new URL[urls.size()];
		URLClassLoader classLoader = new URLClassLoader(urls.toArray(urlArray),
				this.getClass().getClassLoader());

		for (String arg : line.getArgs()) {
			try {

				Class<GeneratorInterface> c = (Class<GeneratorInterface>) classLoader
						.loadClass(getClassForID(arg));

				m_generators.add(injector.getInstance(c));

			} catch (Exception e) {
				logger.error("Invalid generator type : " + arg);
				e.printStackTrace();
				return -1;
			}

		}

		for (GeneratorInterface generator : m_generators) {
			generator.generate(fModel, fInterfaces, f, null);
		}

		logger.info("FrancaStandaloneGen done.");
		return 0;
	}

	static String getClassForID(String arg) {
		String className = new String();
		className += GENERATOR_CLASS_PREFIX + arg + GENERATOR_CLASS_SUFFIX;
		return className;
	}

	@SuppressWarnings("static-access")
	private Options getOptions() {
		// String[] set = LogFactory.getLog(getClass()).
		final Options options = new Options();

		// optional
		// Option optVerbose = OptionBuilder.withArgName("verbose")
		// .withDescription("Print Out Verbose Information").hasArg(false)
		// .isRequired(false).create(VERBOSE);
		// options.addOption(optVerbose);
		Option optHelp = OptionBuilder.withArgName("help")
				.withDescription("Print Usage Information").hasArg(false)
				.isRequired(false).create(HELP);
		options.addOption(optHelp);

		// required
		Option optInputFidl = OptionBuilder
				.withArgName("Franca deployment file")
				.withDescription(
						"Input file in Franca deployment (fdepl) format.")
				.hasArg().isRequired().withValueSeparator(' ').create(FIDLFILE);
		// optInputFidl.setType(File.class);
		options.addOption(optInputFidl);

		Option optOutputDir = OptionBuilder
				.withArgName("output directory")
				.withDescription(
						"Directory where the generated files will be stored")
				.hasArg().isRequired().withValueSeparator(' ').create(OUTDIR);
		options.addOption(optOutputDir);
		
		Option optGeneratorsPath = OptionBuilder
				.withArgName("generators path")
				.withDescription(
						"Directory containing the JAR file for the various generators")
				.hasArg().isRequired().withValueSeparator(' ').create(GENERATORS_PATH);
		options.addOption(optGeneratorsPath);

		return options;
	}

	private boolean checkCommandLineValues(CommandLine line) {
		if (line.hasOption(FIDLFILE)) {
			String fidlFile = line.getOptionValue(FIDLFILE);
			File fidl = new File(fidlFile);
			if (fidl.exists()) {
				return true;
			} else {
				logger.error("Cannot open Franca IDL file '" + fidlFile + "'.");
			}
		}
		return false;
	}

}
