//===========================================================================
//    Copyright 2014 Delving B.V.
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//===========================================================================
package eu.delving.x3ml;

import eu.delving.x3ml.engine.Generator;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import static eu.delving.x3ml.X3MLEngine.exception;

/**
 * Using commons-cli to make the engine usable on the command line.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class X3MLCommandLine {
    static final CommandLineParser PARSER = new PosixParser();
    static final HelpFormatter HELP = new HelpFormatter();
    static Options options = new Options();

    static void error(String message) {
        HELP.setDescPadding(5);
        HELP.setLeftPadding(5);
        HELP.printHelp(
                200,
                "x3ml -xml <input records> -x3ml <mapping file> hello",
                "Options",
                options,
                message
        );
        System.exit(1);
    }

    public static void main(String[] args) {
        Option xml = new Option(
                "xml", true,
                "XML input records: -xml input.xml (@ = stdin)"
        );
        xml.setRequired(true);
        Option x3ml = new Option(
                "x3ml", true,
                "X3ML mapping definition: -x3ml mapping.x3ml (@ = stdin)"
        );
        x3ml.setRequired(true);
        Option rdf = new Option(
                "rdf", true,
                "The RDF output file name: -rdf output.rdf"
        );
        Option policy = new Option(
                "policy", true,
                "The value policy file: -policy policy.xml"
        );
        Option rdfFormat = new Option(
                "format", true,
                "Output format: -format application/n-triples, text/turtle, application/rdf+xml (default)"
        );
        Option validate = new Option(
                "validate", false,
                "Validate X3ML v1.0 using XSD"
        );
        Option uuidTestSize = new Option(
                "uuidTestSize", true,
                "Create a test UUID generator of the given size. Default is UUID from operating system"
        );
        options.addOption(rdfFormat).addOption(rdf).addOption(x3ml).addOption(xml).addOption(policy)
                .addOption(validate).addOption(uuidTestSize);
        try {
            CommandLine cli = PARSER.parse(options, args);
            int uuidTestSizeValue = -1;
            String uuidTestSizeString = cli.getOptionValue("uuidTestSize");
            if (uuidTestSizeString != null) {
                uuidTestSizeValue = Integer.parseInt(uuidTestSizeString);
            }
            go(
                    cli.getOptionValue("xml"),
                    cli.getOptionValue("x3ml"),
                    cli.getOptionValue("policy"),
                    cli.getOptionValue("rdf"),
                    cli.getOptionValue("format"),
                    cli.hasOption("validate"),
                    uuidTestSizeValue
            );
        }
        catch (Exception e) {
            error(e.getMessage());
        }

    }

    static File file(String name) {
        File file = new File(name);
        if (!file.exists() || !file.isFile()) {
            error("File does not exist: " + name);
        }
        return file;
    }

    static DocumentBuilderFactory documentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    static Element xml(InputStream inputStream) {
        try {
            DocumentBuilder builder = documentBuilderFactory().newDocumentBuilder();
            Document document = builder.parse(inputStream);
            return document.getDocumentElement();
        }
        catch (Exception e) {
            throw exception("Unable to parse XML input");
        }
    }

    static Element xml(File file) {
        return xml(getStream(file));
    }

    static FileInputStream getStream(File file) {
        try {
            return new FileInputStream(file);
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static Generator getValuePolicy(String policy, X3MLGeneratorPolicy.UUIDSource uuidSource) {
        FileInputStream stream = null;
        if (policy != null) {
            stream = getStream(file(policy));
        }
        return X3MLGeneratorPolicy.load(stream, uuidSource);
    }

    static PrintStream rdf(String file) {
        if (file != null) {
            try {
                return new PrintStream(new File(file));
            }
            catch (FileNotFoundException e) {
                error(e.getMessage());
                return null;
            }
        }
        else {
            return System.out;
        }
    }

    static void go(String xml, String x3ml, String policy, String rdf, String rdfFormat, boolean validate, int uuidTestSize) {
        Element xmlElement;
        if ("@".equals(xml)) {
            xmlElement = xml(System.in);
        }
        else {
            xmlElement = xml(file(xml));
        }
        InputStream x3mlStream;
        if ("@".equals(x3ml)) {
            if (validate) {
                throw exception("Cannot validate when X3ML is piped");
            }
            x3mlStream = System.in;
        }
        else {
            if (validate) {
                List<String> errors = X3MLEngine.validate(getStream(file(x3ml)));
                if (!errors.isEmpty()) {
                    System.out.println("Validation:");
                    for (String error : errors) {
                        System.out.println(error);
                    }
                    return;
                }
            }
            x3mlStream = getStream(file(x3ml));
        }
        X3MLEngine engine = X3MLEngine.load(x3mlStream);
        X3MLEngine.Output output = engine.execute(
                xmlElement,
                getValuePolicy(policy, X3MLGeneratorPolicy.createUUIDSource(uuidTestSize))
        );
        output.write(rdf(rdf), rdfFormat);
    }
}
