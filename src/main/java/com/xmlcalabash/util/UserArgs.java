package com.xmlcalabash.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.transform.sax.SAXSource;

import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.runtime.XLibrary;
import com.xmlcalabash.util.Input.Type;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.EmptyAttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.SingletonAttributeMap;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.xml.sax.InputSource;

import static com.xmlcalabash.core.XProcConstants.NS_XPROC;
import static com.xmlcalabash.core.XProcConstants.p_data;
import static com.xmlcalabash.core.XProcConstants.p_declare_step;
import static com.xmlcalabash.core.XProcConstants.p_document;
import static com.xmlcalabash.core.XProcConstants.p_empty;
import static com.xmlcalabash.core.XProcConstants.p_import;
import static com.xmlcalabash.core.XProcConstants.p_input;
import static com.xmlcalabash.core.XProcConstants.p_output;
import static com.xmlcalabash.core.XProcConstants.p_pipe;
import static com.xmlcalabash.core.XProcConstants.p_with_param;
import static com.xmlcalabash.util.Input.Kind.INPUT_STREAM;
import static com.xmlcalabash.util.Input.Type.DATA;
import static com.xmlcalabash.util.JSONtoXML.knownFlavor;
import static com.xmlcalabash.util.LogOptions.DIRECTORY;
import static com.xmlcalabash.util.LogOptions.OFF;
import static com.xmlcalabash.util.LogOptions.PLAIN;
import static com.xmlcalabash.util.LogOptions.WRAPPED;
import static com.xmlcalabash.util.URIUtils.encode;
import static java.io.File.createTempFile;
import static java.lang.Long.MAX_VALUE;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.net.URLConnection.guessContentTypeFromName;
import static java.net.URLConnection.guessContentTypeFromStream;
import static java.nio.channels.Channels.newChannel;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static net.sf.saxon.s9api.QName.fromClarkName;

public class UserArgs {
    protected boolean needsCheck = false;
    protected Boolean debug = null;
    protected Boolean showMessages = null;
    protected Output profile = null;
    protected boolean showVersion = false;
    protected String saxonProcessor = null;
    protected Input saxonConfig = null;
    protected boolean schemaAware = false;
    protected Boolean safeMode = null;
    protected Input config = null;
    protected String logStyle = null;
    protected String entityResolverClass = null;
    protected String uriResolverClass = null;
    protected Input pipeline = null;
    protected List<Input> libraries = new ArrayList<>();
    protected Map<String, Output> outputs = new HashMap<>();
    protected Map<String, String> bindings = new HashMap<>();
    protected List<StepArgs> steps = new ArrayList<>();
    protected StepArgs curStep = new StepArgs();
    protected StepArgs lastStep = null;
    protected boolean extensionValues = false;
    protected boolean allowXPointerOnText = false;
    protected boolean allowTextResults = false;
    protected boolean useXslt10 = false;
    protected boolean htmlSerializer = false;
    protected boolean ignoreInvalidXmlBase = false;
    protected boolean transparentJSON = false;
    protected String jsonFlavor = null;
    protected Integer piperackPort = null;
    protected Integer piperackExpires = null;
    protected Map<String,String> serParams = new HashMap<>();

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setShowMessages(boolean show) {
        showMessages = show;
    }

    protected void setProfile(Output profile) {
        needsCheck = true;
        if ((this.profile != null) && (profile != null)) {
            throw new XProcException("Multiple profile are not supported.");
        }
        this.profile = profile;
    }

    public void setProfile(String profile) {
        if ("-".equals(profile)) {
            setProfile(new Output(System.out));
        } else {
            setProfile(new Output("file://" + fixUpURI(profile)));
        }
    }

    public void setProfile(OutputStream outputStream) {
        setProfile(new Output(outputStream));
    }

    public boolean isShowVersion() {
        return showVersion;
    }

    public void setShowVersion(boolean showVersion) {
        this.showVersion = showVersion;
    }

    public void setPiperackPort(int port) {
        piperackPort = port;
    }

    public void setPiperackExpires(int seconds) {
        piperackExpires = seconds;
    }

    public void setSaxonProcessor(String saxonProcessor) {
        needsCheck = true;
        this.saxonProcessor = saxonProcessor;
        if ( !("he".equals(saxonProcessor) || "pe".equals(saxonProcessor) || "ee".equals(saxonProcessor)) ) {
            throw new XProcException("Invalid Saxon processor option: '" + saxonProcessor + "'. Must be 'he' (default), 'pe' or 'ee'.");
        }
    }

    protected void setSaxonConfig(Input saxonConfig) {
        needsCheck = true;
        if ((this.saxonConfig != null) && (saxonConfig != null)) {
            throw new XProcException("Multiple saxonConfig are not supported.");
        }
        this.saxonConfig = saxonConfig;
    }

    public void setSaxonConfig(String saxonConfig) {
        if ("-".equals(saxonConfig)) {
            setSaxonConfig(new Input(System.in, "<stdin>"));
        } else {
            setSaxonConfig(new Input("file://" + fixUpURI(saxonConfig)));
        }
    }

    public void setSaxonConfig(InputStream inputStream, String uri) {
        setSaxonConfig(new Input(inputStream, uri));
    }

    public void setSchemaAware(boolean schemaAware) {
        needsCheck = true;
        this.schemaAware = schemaAware;
    }

    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }

    protected void setConfig(Input config) {
        if ((this.config != null) && (config != null)) {
            throw new XProcException("Multiple config are not supported.");
        }
        this.config = config;
    }

    public void setConfig(String config) {
        if ("-".equals(config)) {
            setConfig(new Input(System.in, "<stdin>"));
        } else {
            setConfig(new Input("file://" + fixUpURI(config)));
        }
    }

    public void setConfig(InputStream inputStream, String uri) {
        setConfig(new Input(inputStream, uri));
    }

    public void setLogStyle(String logStyle) {
        this.logStyle = logStyle;
        if (!("off".equals(logStyle) || "plain".equals(logStyle)
              || "wrapped".equals(logStyle) || "directory".equals(logStyle))) {
            throw new XProcException("Invalid log style: '" + logStyle + "'. Must be 'off', 'plain', 'wrapped' (default) or 'directory'.");
        }
    }

    public void setEntityResolverClass(String entityResolverClass) {
        this.entityResolverClass = entityResolverClass;
    }

    public void setUriResolverClass(String uriResolverClass) {
        this.uriResolverClass = uriResolverClass;
    }

    public Input getPipeline() {
        checkArgs();
        return pipeline;
    }

    protected void setPipeline(Input pipeline) {
        needsCheck = true;
        if ((this.pipeline != null) && (pipeline != null)) {
            throw new XProcException("Multiple pipelines are not supported.");
        }
        this.pipeline = pipeline;
    }

    public void setPipeline(String uri) {
        setPipeline(new Input(uri));
    }

    public void setPipeline(InputStream inputStream, String uri) {
        setPipeline(new Input(inputStream, uri));
    }

    public void addLibrary(String libraryURI) {
        needsCheck = true;
        libraries.add(new Input(libraryURI));
    }

    public void addLibrary(InputStream libraryInputStream, String libraryURI) {
        needsCheck = true;
        libraries.add(new Input(libraryInputStream, libraryURI));
    }

    public Map<String, Output> getOutputs() {
        checkArgs();
        return unmodifiableMap(outputs);
    }

    public void addOutput(String port, String uri) {
        if (outputs.containsKey(port)) {
            if (port == null) {
                throw new XProcException("Duplicate output binding for default output port.");
            } else {
                throw new XProcException("Duplicate output binding: '" + port + "'.");
            }
        }

        if ("-".equals(uri)) {
            outputs.put(port, new Output(uri));
        } else {
            URI cwd = URIUtils.cwdAsURI();
            outputs.put(port, new Output(cwd.resolve(uri).toASCIIString()));
        }
    }

    public void addOutput(String port, OutputStream outputStream) {
        if (outputs.containsKey(port)) {
            if (port == null) {
                throw new XProcException("Duplicate output binding for default output port.");
            } else {
                throw new XProcException("Duplicate output binding: '" + port + "'.");
            }
        }

        outputs.put(port, new Output(outputStream));
    }

    public void addBinding(String prefix, String uri) {
        if (bindings.containsKey(prefix)) {
            throw new XProcException("Duplicate prefix binding: '" + prefix + "'.");
        }

        bindings.put(prefix, uri);
    }

    public void setCurStepName(String name) {
        needsCheck = true;
        curStep.setName(name);
        steps.add(curStep);
        lastStep = curStep;
        curStep = new StepArgs();
    }

    public Set<String> getInputPorts() {
        checkArgs();
        if (steps.size() != 0) {
            // If we built a compound pipeline from the arguments, then there aren't any pipeline inputs
            return emptySet();
        }
        return unmodifiableSet(curStep.inputs.keySet());
    }

    public List<Input> getInputs(String port) {
        checkArgs();
        if (steps.size() != 0) {
            // If we built a compound pipeline from the arguments, then there aren't any pipeline inputs
            return emptyList();
        }
        return unmodifiableList(curStep.inputs.get(port));
    }

    public void addInput(String port, String uri, Type type) {
        addInput(port, uri, type, null);
    }

    public void addInput(String port, String uri, Type type, String contentType) {
        if ("-".equals(uri)  || "p:empty".equals(uri)) {
            curStep.addInput(port, uri, type, contentType);
        } else {
            URI cwd = URIUtils.cwdAsURI();
            curStep.addInput(port, cwd.resolve(uri).toASCIIString(), type, contentType);
        }
    }

    public void addInput(String port, InputStream inputStream, String uri, Type type) throws IOException {
        addInput(port, inputStream, uri, type, null);
    }

    public void addInput(String port, InputStream inputStream, String uri, Type type, String contentType) throws IOException {
        inputStream = new BufferedInputStream(inputStream);
        contentType = ((contentType == null) || "content/unknown".equals(contentType)) ? guessContentTypeFromStream(inputStream) : contentType;
        contentType = ((contentType == null) || "content/unknown".equals(contentType)) ? guessContentTypeFromName(uri) : contentType;
        curStep.addInput(port, inputStream, uri, type, contentType);
    }

    public void setDefaultInputPort(String port) {
        if (curStep.inputs.containsKey(null)) {
            curStep.inputs.put(port, curStep.inputs.remove(null));
        }
    }

    public Set<String> getParameterPorts() {
        checkArgs();
        if (steps.size() != 0) {
            // If we built a compound pipeline from the arguments, then there aren't any pipeline parameters
            return emptySet();
        }
        return unmodifiableSet(curStep.params.keySet());
    }

    public Map<QName, String> getParameters(String port) {
        checkArgs();
        if (steps.size() != 0) {
            // If we built a compound pipeline from the arguments, then there aren't any pipeline parameters
            return emptyMap();
        }
        return unmodifiableMap(curStep.params.get(port));
    }

    public void addParam(String name, String value) {
        needsCheck = true;
        String port = "*";

        int cpos = name.indexOf("@");
        if (cpos > 0) {
            port = name.substring(0, cpos);
            name = name.substring(cpos + 1);
        }

        curStep.addParameter(port, name, value);
    }

    public void addParam(String port, String name, String value) {
        needsCheck = true;
        curStep.addParameter(port, name, value);
    }

    public Set<QName> getOptionNames() {
        checkArgs();
        if (steps.size() != 0) {
            // If we built a compound pipeline from the arguments, then there aren't any pipeline options
            return emptySet();
        }
        return unmodifiableSet(curStep.options.keySet());
    }

    public String getOption(QName name) {
        checkArgs();
        if (steps.size() != 0) {
            // If we built a compound pipeline from the arguments, then there aren't any pipeline options
            return null;
        }
        return curStep.options.get(name);
    }

    public void addOption(String name, String value) {
        needsCheck = true;
        if (lastStep != null) {
            lastStep.addOption(name, value);
        } else {
            curStep.addOption(name, value);
        }
    }

    public void setExtensionValues(boolean extensionValues) {
        this.extensionValues = extensionValues;
    }

    public void setAllowXPointerOnText(boolean allowXPointerOnText) {
        this.allowXPointerOnText = allowXPointerOnText;
    }

    public void setAllowTextResults(boolean allowTextResults) {
        this.allowTextResults = allowTextResults;
    }

    public void setUseXslt10(boolean useXslt10) {
        this.useXslt10 = useXslt10;
    }

    public void setHtmlSerializer(boolean htmlSerializer) {
        this.htmlSerializer = htmlSerializer;
    }

    public void setIgnoreInvalidXmlBase(boolean ignore) {
        this.ignoreInvalidXmlBase = ignore;
    }

    public void setTransparentJSON(boolean transparentJSON) {
        this.transparentJSON = transparentJSON;
    }

    public void setJsonFlavor(String jsonFlavor) {
        this.jsonFlavor = jsonFlavor;
        if ((jsonFlavor != null) && !knownFlavor(jsonFlavor)) {
            throw new XProcException("Unknown JSON flavor: '" + jsonFlavor + "'.");
        }
    }

    public void setSerializationParameter(String port, String param, String value) {
        if (port == null) {
            port = "*";
        }
        if (param.equals("byte-order-mark")
                || param.equals("cdata-section-elements")
                || param.equals("escape-uri-attributes")
                || param.equals("include-content-type")
                || param.equals("indent")
                || param.equals("omit-xml-declaration")
                || param.equals("undeclare-prefixes")
                || param.equals("method")
                || param.equals("doctype-public")
                || param.equals("doctype-system")
                || param.equals("encoding")
                || param.equals("media-type")
                || param.equals("normalization-form")
                || param.equals("standalone")
                || param.equals("version")) {
            serParams.put(port + ":" + param, value);
        } else {
            throw new XProcException("Unsupported or unrecognized serialization parameter: " + param);
        }
    }

    public String getSerializationParameter(String port, String param) {
        return serParams.getOrDefault(port + ":" + param, null);
    }

    public String getSerializationParameter(String param) {
        return serParams.getOrDefault("*:" + param, null);
    }

    /**
     * This method does some sanity checks and should be called at the
     * beginning of every public method that has a return value to make
     * sure that no invalid argument combinations are used.
     *
     * It is public so that it can also be invoked explicitly to control
     * the time when these checks are done.
     *
     * @throws XProcException if something is not valid in the arguments
     */
    public void checkArgs() {
        if (needsCheck) {
            if (hasImplicitPipelineInternal() && (pipeline != null)) {
                throw new XProcException("You can specify a library and / or steps or a pipeline, but not both.");
            }

            if (saxonConfig != null) {
                if (schemaAware) {
                    throw new XProcException("Specifying schema-aware processing is an error if you specify a Saxon configuration file.");
                }
                if (saxonProcessor != null) {
                    throw new XProcException("Specifying a processor type is an error if you specify a Saxon configuration file.");
                }
            }

            if (schemaAware && (saxonProcessor != null) && !"ee".equals(saxonProcessor)) {
                throw new XProcException("Schema-aware processing can only be used with saxon processor \"ee\".");
            }

            for (StepArgs step : steps) {
                step.checkArgs();
            }

            if (!steps.contains(curStep)) {
                curStep.checkArgs();
            }

            needsCheck = false;
        }
    }

    public XProcConfiguration createConfiguration() {
        checkArgs();
        XProcConfiguration config = null;

        // Blech
        try {
            String proc = saxonProcessor;
            if (schemaAware) {
                proc = "ee";
            }

            if (saxonConfig != null) {
                config = new XProcConfiguration(saxonConfig);
            } else if (proc != null) {
                config = new XProcConfiguration(proc, schemaAware);
            } else {
                config = new XProcConfiguration();
            }
        } catch (Exception e) {
            err.println("FATAL: Failed to parse Saxon configuration file.");
            err.println(e);
            exit(2);
        }

        if (this.config != null) {
            try {
                InputStream instream;
                switch (this.config.getKind()) {
                    case URI:
                        URI furi = URI.create(this.config.getUri());
                        instream = new FileInputStream(new File(furi));
                        break;

                    case INPUT_STREAM:
                        instream = this.config.getInputStream();
                        break;

                    default:
                        throw new UnsupportedOperationException(format("Unsupported config kind '%s'", this.config.getKind()));
                }


                SAXSource source = new SAXSource(new InputSource(instream));
                // No resolver, we don't have one yet
                DocumentBuilder builder = config.getProcessor().newDocumentBuilder();
                XdmNode doc = builder.build(source);
                config.parse(doc);
            } catch (Exception e) {
                err.println("FATAL: Failed to parse configuration file.");
                err.println(e);
                exit(3);
            }
        }

        if (logStyle != null) {
            switch (logStyle) {
                case "off":
                    config.logOpt = OFF;
                    break;
                case "plain":
                    config.logOpt = PLAIN;
                    break;
                case "directory":
                    config.logOpt = DIRECTORY;
                    break;
                default:
                    config.logOpt = WRAPPED;
                    break;
            }
        }

        if (uriResolverClass != null) {
            config.uriResolver = uriResolverClass;
        }

        if (entityResolverClass != null) {
            config.entityResolver = entityResolverClass;
        }

        if (safeMode != null) {
            config.safeMode = safeMode;
        }

        if (debug != null) {
            config.debug = debug;
        }

        if (showMessages != null) {
            config.showMessages = showMessages;
        }

        if (profile != null) {
            config.profile = profile;
        }

        config.extensionValues |= extensionValues;
        config.xpointerOnText |= allowXPointerOnText;
        config.ignoreInvalidXmlBase |= ignoreInvalidXmlBase;
        config.transparentJSON |= transparentJSON;
        if ((jsonFlavor != null) && !knownFlavor(jsonFlavor)) {
            config.jsonFlavor = jsonFlavor;
        }
        config.allowTextResults |= allowTextResults;
        config.useXslt10 |= useXslt10;
        config.htmlSerializer |= htmlSerializer;

        if (piperackPort != null) {
            config.piperackPort = piperackPort;
        }

        if (piperackExpires != null) {
            config.piperackDefaultExpires = piperackExpires;
        }

        return config;
    }

    /**
     * Helper method to prevent an endless-loop when using
     * {@link #hasImplicitPipeline} from within {@link #checkArgs()}
     *
     * @return whether an implicit pipeline is used
     */
    private boolean hasImplicitPipelineInternal() {
        return (steps.size() > 0) || (libraries.size() > 0);
    }

    public boolean hasImplicitPipeline() {
        checkArgs();
        return hasImplicitPipelineInternal();
    }

    public XdmNode getImplicitPipeline(XProcRuntime runtime) throws IOException {
        checkArgs();
        // This is a bit of a hack...
        if (steps.size() == 0 && libraries.size() > 0) {
            try {
                Input library = libraries.get(0);
                if (library.getKind() == INPUT_STREAM) {
                    InputStream libraryInputStream = library.getInputStream();
                    FileOutputStream fileOutputStream = null;
                    try {
                        File tempLibrary = createTempFile("calabashLibrary", null);
                        tempLibrary.deleteOnExit();
                        fileOutputStream = new FileOutputStream(tempLibrary);
                        fileOutputStream.getChannel().transferFrom(newChannel(libraryInputStream), 0, MAX_VALUE);
                        libraries.set(0, new Input(tempLibrary.toURI().toASCIIString()));
                    } finally {
                        Closer.close(fileOutputStream);
                        libraryInputStream.close();
                    }
                }

                XLibrary xLibrary = runtime.loadLibrary(libraries.get(0));
                curStep.setName(xLibrary.getFirstPipelineType().getClarkName());
                curStep.checkArgs();
                steps.add(curStep);
            } catch (SaxonApiException sae) {
                throw new XProcException(sae);
            }
        }

        TreeWriter tree = new TreeWriter(runtime);
        tree.startDocument(runtime.getStaticBaseURI());
        tree.addStartElement(p_declare_step, SingletonAttributeMap.of(TypeUtils.attributeInfo(new QName("version"), "1.0")));

        AttributeMap attr = EmptyAttributeMap.getInstance();
        attr = attr.put(TypeUtils.attributeInfo(new QName("port"), "parameters"));
        attr = attr.put(TypeUtils.attributeInfo(new QName("kind"), "parameter"));
        tree.addStartElement(p_input, attr);
        tree.addEndElement();

        // This is a hack too. If there are no outputs, fake one.
        // Implicit pipelines default to having a single primary output port names "result"
        if (outputs.size() == 0) {
            outputs.put("result", new Output("-"));
        }

        String lastStepName = "cmdlineStep" + steps.size();
        for (String port : outputs.keySet()) {
            if (port == null) {
                port = "result";
            }
            tree.addStartElement(p_output, SingletonAttributeMap.of(TypeUtils.attributeInfo(new QName("port"), port)));

            attr = EmptyAttributeMap.getInstance();
            attr = attr.put(TypeUtils.attributeInfo(new QName("step"), lastStepName));
            attr = attr.put(TypeUtils.attributeInfo(new QName("port"), port));
            tree.addStartElement(p_pipe, attr);

            tree.addEndElement();
            tree.addEndElement();
        }

        for (Input library : libraries) {
            switch (library.getKind()) {
                case URI:
                    tree.addStartElement(p_import, SingletonAttributeMap.of(TypeUtils.attributeInfo(new QName("href"), library.getUri())));
                    tree.addEndElement();
                    break;

                case INPUT_STREAM:
                    InputStream libraryInputStream = library.getInputStream();
                    FileOutputStream fileOutputStream = null;
                    try {
                        File tempLibrary = createTempFile("calabashLibrary", null);
                        tempLibrary.deleteOnExit();
                        fileOutputStream = new FileOutputStream(tempLibrary);
                        fileOutputStream.getChannel().transferFrom(newChannel(libraryInputStream), 0, MAX_VALUE);

                        tree.addStartElement(p_import, SingletonAttributeMap.of(TypeUtils.attributeInfo(new QName("href"), tempLibrary.toURI().toASCIIString())));
                        tree.addEndElement();
                    } finally {
                        Closer.close(fileOutputStream);
                        libraryInputStream.close();
                    }
                    break;

                default:
                    throw new UnsupportedOperationException(format("Unsupported library kind '%s'", library.getKind()));
            }
        }

        int stepNum = 0;
        for (StepArgs step : steps) {
            stepNum ++;

            attr = EmptyAttributeMap.getInstance();
            attr = attr.put(TypeUtils.attributeInfo(new QName("name"), "cmdlineStep" + stepNum));
            for (QName optname : step.options.keySet()) {
                attr = attr.put(TypeUtils.attributeInfo(optname, step.options.get(optname)));
            }
            tree.addStartElement(step.stepName, attr);

            for (String port : step.inputs.keySet()) {
                tree.addStartElement(p_input, SingletonAttributeMap.of(TypeUtils.attributeInfo(new QName("port"), (port == null) ? "source" : port)));

                for (Input input : step.inputs.get(port)) {
                    QName qname = (input.getType() == DATA) ? p_data : p_document;
                    switch (input.getKind()) {
                        case URI:
                            String uri = input.getUri();
                            if ("p:empty".equals(uri)) {
                                tree.addStartElement(p_empty);
                            } else {
                                attr = EmptyAttributeMap.getInstance();
                                attr = attr.put(TypeUtils.attributeInfo(new QName("href"), uri));
                                if (input.getType() == DATA) {
                                    attr = attr.put(TypeUtils.attributeInfo(new QName("content-type"), input.getContentType()));
                                }
                                tree.addStartElement(qname, attr);
                            }
                            tree.addEndElement();
                            break;

                        case INPUT_STREAM:
                            InputStream inputStream = input.getInputStream();
                            if (System.in.equals(inputStream)) {
                                attr = EmptyAttributeMap.getInstance();
                                attr = attr.put(TypeUtils.attributeInfo(new QName("href"), "-"));
                                if (input.getType() == DATA) {
                                    attr = attr.put(TypeUtils.attributeInfo(new QName("content-type"), input.getContentType()));
                                }
                                tree.addStartElement(qname, attr);
                                tree.addEndElement();
                            } else {
                                FileOutputStream fileOutputStream = null;
                                try {
                                    File tempInput = createTempFile("calabashInput", null);
                                    tempInput.deleteOnExit();
                                    fileOutputStream = new FileOutputStream(tempInput);
                                    fileOutputStream.getChannel().transferFrom(newChannel(inputStream), 0, MAX_VALUE);

                                    attr = EmptyAttributeMap.getInstance();
                                    attr = attr.put(TypeUtils.attributeInfo(new QName("href"), tempInput.toURI().toASCIIString()));
                                    if (input.getType() == DATA) {
                                        attr = attr.put(TypeUtils.attributeInfo(new QName("content-type"), input.getContentType()));
                                    }
                                    tree.addStartElement(qname, attr);
                                    tree.addEndElement();
                                } finally {
                                    Closer.close(fileOutputStream);
                                    inputStream.close();
                                }
                            }
                            break;

                        default:
                            throw new UnsupportedOperationException(format("Unsupported input kind '%s'", input.getKind()));
                    }
                }
                tree.addEndElement();
            }

            for (String port : step.params.keySet()) {
                for (QName pname : step.params.get(port).keySet()) {
                    String value = step.params.get(port).get(pname);
                    // Double single quotes to escape them between the enclosing single quotes
                    value = "'" + value.replace("'", "''") + "'";

                    NamespaceMap nsmap = NamespaceMap.emptyMap();
                    attr = EmptyAttributeMap.getInstance();
                    attr = attr.put(TypeUtils.attributeInfo(new QName("name"), pname.toString()));
                    attr = attr.put(TypeUtils.attributeInfo(new QName("select"), value));
                    if (!"*".equals(port)) {
                        attr = attr.put(TypeUtils.attributeInfo(new QName("port"), port));
                    }
                    if (!pname.getPrefix().isEmpty() || !pname.getNamespaceURI().isEmpty()) {
                        nsmap = nsmap.put(pname.getPrefix(), pname.getNamespaceURI());
                    }

                    tree.addStartElement(p_with_param, attr, nsmap);
                    tree.addEndElement();
                }
            }

            tree.addEndElement();
        }

        tree.addEndElement();
        tree.endDocument();

        return tree.getResult();
    }

    private String fixUpURI(String uri) {
        File f = new File(uri);
        String fn = encode(f.getAbsolutePath());
        // FIXME: HACK!
        if ("\\".equals(getProperty("file.separator"))) {
            fn = "/" + fn;
        }
        return fn;
    }

    private class StepArgs {
        public String plainStepName = null;
        public QName stepName = null;
        public Map<String, List<Input>> inputs = new HashMap<>();
        public Map<String, Map<String, String>> plainParams = new HashMap<>();
        public Map<String, Map<QName, String>> params = new HashMap<>();
        public Map<String, String> plainOptions = new HashMap<>();
        public Map<QName, String> options = new HashMap<>();

        public void setName(String name) {
            needsCheck = true;
            this.plainStepName = name;
        }

        public void addInput(String port, String uri, Type type, String contentType) {
            if (!inputs.containsKey(port)) {
                inputs.put(port, new ArrayList<>());
            }

            inputs.get(port).add(new Input(uri, type, contentType));
        }

        public void addInput(String port, InputStream inputStream, String uri, Type type, String contentType) {
            if (!inputs.containsKey(port)) {
                inputs.put(port, new ArrayList<>());
            }

            inputs.get(port).add(new Input(inputStream, uri, type, contentType));
        }

        public void addOption(String optname, String value) {
            needsCheck = true;
            if (plainOptions.containsKey(optname)) {
                throw new XProcException("Duplicate option name: '" + optname + "'.");
            }

            plainOptions.put(optname, value);
        }

        public void addParameter(String port, String name, String value) {
            needsCheck = true;
            Map<String, String> portParams;
            if (!plainParams.containsKey(port)) {
                portParams = new HashMap<>();
            } else {
                portParams = plainParams.get(port);
            }

            if (portParams.containsKey(name)) {
                throw new XProcException("Duplicate parameter name: '" + name + "'.");
            }

            portParams.put(name, value);
            plainParams.put(port, portParams);
        }

        private QName makeQName(String name) {
            QName qname;

            if (name == null) {
                qname = new QName("");
            } else if (name.indexOf("{") == 0) {
                qname = fromClarkName(name);
            } else {
                int cpos = name.indexOf(":");
                if (cpos > 0) {
                    String prefix = name.substring(0, cpos);
                    if (!bindings.containsKey(prefix)) {
                        throw new XProcException("Unbound prefix '" + prefix + "' in: '" + name + "'.");
                    }
                    String uri = bindings.get(prefix);
                    qname = new QName(prefix, uri, name.substring(cpos + 1));
                } else {
                    qname = new QName("", name);
                }
            }

            return qname;
        }

        public void checkArgs() {
            // Make this check here, to ensure it is done before makeQName
            // is used. Otherwise it would have to be guaranteed that this
            // check is done before calling this method.
            //
            // Default the prefix "p" to the XProc namespace
            if (!bindings.containsKey("p")) {
                bindings.put("p", NS_XPROC);
            }

            stepName = makeQName(plainStepName);
            if ("".equals(stepName.getNamespaceURI())) {
                stepName = new QName("p", "http://www.w3.org/ns/xproc", stepName.getLocalName());
            }

            options.clear();
            for (Entry<String, String> plainOption : plainOptions.entrySet()) {
                QName name = makeQName(plainOption.getKey());
                if (options.containsKey(name)) {
                    throw new XProcException("Duplicate option name: '" + name + "'.");
                }
                options.put(name, plainOption.getValue());
            }

            params.clear();
            for (Entry<String, Map<String, String>> plainParam : plainParams.entrySet()) {
                Map<QName, String> portParams = new HashMap<>();
                for (Entry<String, String> portParam : plainParam.getValue().entrySet()) {
                    QName name = makeQName(portParam.getKey());
                    if (portParams.containsKey(name)) {
                        throw new XProcException("Duplicate parameter name: '" + name + "'.");
                    }
                    portParams.put(name, portParam.getValue());
                }
                params.put(plainParam.getKey(), portParams);
            }
        }
    }
}
