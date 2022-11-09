package org.fosstrak.ale.server.readers.impinj;

import org.apache.log4j.Logger;
import org.fosstrak.ale.exception.ImplementationException;
import org.fosstrak.ale.server.Tag;
import org.fosstrak.ale.server.readers.BaseReader;
import org.fosstrak.ale.server.util.TagHelper;
import org.fosstrak.ale.xsd.ale.epcglobal.CCOpSpec;
import org.fosstrak.ale.xsd.ale.epcglobal.CCSpec;
import org.fosstrak.ale.xsd.ale.epcglobal.LRSpec;
import org.fosstrak.hal.HardwareException;
import org.fosstrak.hal.Observation;
import org.fosstrak.tdt.TDTEngine;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import shaded.impinj.octancesdk.*;
import static org.fosstrak.ale.util.HexUtil.byteArrayToBinString;
import static org.fosstrak.ale.util.HexUtil.byteArrayToHexString;

public class SpeedwayAdapter extends BaseReader implements TagReportListener {

    /** Logger */
    private static final Logger log = Logger.getLogger(SpeedwayAdapter.class);

    /** Variables */
    private String ip;
    private int port = 5084;
    private boolean readTID = false;
    private ImpinjReader reader;
    private TDTEngine tdt;

    /**
     * Constructor
     */
    public SpeedwayAdapter() {
        reader = new ImpinjReader();
        tdt = TagHelper.getTDTEngine();
    }

    /**
     * initializes adapter. this method must be called before the Adaptor can
     * be used.
     * @param name the name for the reader encapsulated by this adaptor.
     * @param spec the specification that describes the current reader.
     * @throws ImplementationException whenever an internal error occurs.
     */
    public void initialize(String name, LRSpec spec) throws ImplementationException {
        super.initialize(name, spec);

        /* Check arguments */
        if ((name == null) || (spec == null)) {
            log.error("Reader name or LRSpec is null.");
            throw new ImplementationException("Reader name or LRSpec is null.");
        }

        /* Get IP */
        ip = logicalReaderProperties.get("IP");
        if ((ip == null) || (ip.isEmpty()))
        {
            log.error("Reader IP is not defined.");
        }

        /* Get port (optional) */
        String portString = logicalReaderProperties.get("Port");
        if ((portString == null) || (portString.isEmpty())) {
            log.info("Reader port is not defined, using default: " + port);
        } else {
            port = Integer.parseInt(portString);
        }

        /* Should read TID ? */
        String readTIDString = logicalReaderProperties.get("ReadTID");
        if (readTIDString != null) {
            if (readTIDString.equalsIgnoreCase("true")) {
                readTID = true;
            } else if (readTIDString.equalsIgnoreCase("false")) {
                readTID = false;
            } else {
                log.error("Invalid ReadTID value (" + readTIDString + "), using default: false");
            }
        }
    }

    @Override
    public void start() {
        log.info("Starting ImpinJ Speedway reader...");

        /* Already started ? */
        if (isStarted()) {
            log.info("Reader is already started");
            return;
        }

        /* Connect reader first */
        if (!isConnected()) {
            connectReader();

            /* Connection successful ? */
            if (!isConnected()) {
                log.error("Failed to started");
                return;
            }
        }

        /* Configure reader */
        Settings settings = reader.queryDefaultSettings();
        ReportConfig report = settings.getReport();
        report.setIncludeAntennaPortNumber(true);
        report.setIncludeFastId(readTID);
        report.setMode(ReportMode.Individual);

        // The reader can be set into various modes in which reader
        // dynamics are optimized for specific regions and environments.
        // The following mode, AutoSetDenseReaderDeepScan, monitors RF noise and interference and then automatically
        // and continuously optimizes the reader's configuration
        settings.setRfMode(1002);

        /* Set listener */
        reader.setTagReportListener(this);

        /* Start continuous read */
        try {
            reader.start();
        } catch (Exception e) {
            log.error("Failed to start continuous read: " + e.getMessage());
            e.printStackTrace();
            setStopped();
            return;
        }

        /* Started */
        log.info("ImpinJ Speedway reader started");
        setStarted();
    }

    @Override
    public void stop() {
        log.info("Stopping ImpinJ Speedway reader...");

        /* Already stopped ? */
        if (!isStarted()) {
            log.info("Reader is already stopped");
            return;
        }

        /* Stop continuous read */
        try {
            reader.stop();
        } catch (Exception e) {
            log.error("Failed to abort continuous read: " + e.getMessage());
            e.printStackTrace();
        }

        /* Stopped */
        log.info("ImpinJ Speedway reader stopped");
        setStopped();
    }

    /**
     * Connect reader
     */
    @Override
    public void connectReader() {

        /* Try to connect */
        try {
            log.info("Connecting to " + ip + " TCP port " + port + "...");
            reader.connect(ip, port);
        } catch (Exception e) {
            log.error("Failed to connect: " + e.getMessage());
            setDisconnected();
            return;
        }

        /* Try to get info */
        try {
            FeatureSet featureSet = reader.queryFeatureSet();
            log.info("Model " + featureSet.getModelName() + ", serial " + featureSet.getSerialNumber() + ", firmware " + featureSet.getFirmwareVersion());
            log.info("Antennas: " + featureSet.getAntennaCount());
        } catch (Exception e) {
            log.error("Failed to get reader feature set: " + e.getMessage());
            setDisconnected();
            return;
        }

        /* Connected now */
        log.info("Connected");
        setConnected();
    }

    /**
     * Disconnect reader
     */
    @Override
    public void disconnectReader() {

        /* Try to disconnect */
        try {
            log.info("Disconnecting from " + ip + " TCP port " + port + "...");
            reader.disconnect();
        } catch (Exception e) {
            log.error("Error during disconnection: " + e.getMessage());
        }

        /* Disconnected anyway */
        log.info("Disconnected");
        setDisconnected();
    }

    @Override
    public void update(LRSpec spec) throws ImplementationException {

    }

    @Override
    public Observation[] identify(String[] readPointNames) throws HardwareException {
        return new Observation[0];
    }

    @Override
    public void ADDACCESSSPECfromCCSpec(CCSpec ccspec, Hashtable<Integer, CCOpSpec> OpSpecTable) {

    }

    @Override
    public void DELETEACCESSSPEC() {

    }

    @Override
    public void recoveryACCESSSPEC3() {

    }

    /**
     * Report tag to observers.
     * @param tag Single tags
     */
    @Override
    public void addTag(Tag tag) {
        setChanged();
        notifyObservers(tag);
    }

    /**
     * Report tags to observers.
     * @param tags List of tags
     */
    @Override
    public void addTags(List<Tag> tags) {
        setChanged();
        notifyObservers(tags);
    }

    @Override
    public void onTagReported(ImpinjReader rdr, TagReport report) {
        List<Tag> tags = new LinkedList<Tag>();

        for (com.impinj.octane.Tag obj : report.getTags()) {

            Tag tag = new Tag();
            tag.setReader(getName());
            tag.setOrigin(rdr.getName());
            tag.addTrace(rdr.getName());
            tag.addTrace("Antenna-" + obj.getAntennaPortNumber());
            tag.setTimestamp(obj.getLastSeenTime().getLocalDateTime().toInstant().toEpochMilli());

            /* ID's */
            //tag.setTagID(obj.getEpc().toWordList());
            tag.setTagAsHex(obj.getEpc().toHexString());
            //tag.setTagAsBinary(byteArrayToBinString(notify.getTagID()));

            /* TID ? */
            if (readTID && obj.isFastIdPresent()) {
                tag.setTidBank(obj.getTid().toHexString());
            }

            /* URI's */
            try {
                String pureID = TagHelper.convert_to_PURE_IDENTITY(null, null, null, tag.getTagAsBinary());
                tag.setTagIDAsPureURI(pureID);

                String epc_tag = TagHelper.convert_to_TAG_ENCODING(null, null, null, tag.getTagAsBinary(), tdt);
                tag.setTagIDAsTagURI(epc_tag);

            } catch (Exception e) {
                /* Treat it as "debug" event because invalid/unprogrammed tags should not be treated as errors */
                log.debug("Tag decoding error: " + e.getMessage());
            }

            tags.add(tag);
        }

        if (tags.size() > 0) {
            addTags(tags);
        }
    }
}
