package org.fosstrak.ale.server.readers.caenrfid;

import java.math.BigInteger;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import com.caen.RFIDLibrary.*;
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

/**
 * CAEN RFID Proton reader adapter
 */
public class ProtonAdapter extends BaseReader implements CAENRFIDEventListener {

    /** Logger */
    private static final Logger log = Logger.getLogger(ProtonAdapter.class);

    /** Variables */
    private String ip;
    private int port;
    private String[] sourceNames;
    private CAENRFIDReader reader;
    private TDTEngine tdt;

    /**
     * Constructor
     */
    public ProtonAdapter() {
        reader = new CAENRFIDReader();
        reader.addCAENRFIDEventListener(this);
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

        /* Get port */
        String portString = logicalReaderProperties.get("Port");
        if ((portString == null) || (portString.isEmpty())) {
            log.error("Reader port is not defined.");
        }

        port = Integer.parseInt(portString);

        /* Get sources */
        String sources = logicalReaderProperties.get("Sources");
        if ((sources == null) || (sources.isEmpty())) {
            log.error("No sources defined.");
        }
        sourceNames = sources.split(",");

        /* Check sources */
        for (String sourceName : sourceNames) {
            try {
                CAENRFIDLogicalSource source = reader.GetSource(sourceName);
            } catch (CAENRFIDException e) {
                log.error("Invalid source (" + sourceName + "): " + e.getMessage());
            }
        }
    }

    @Override
    public void start() {
        log.debug("Starting CAEN RFID Proton reader...");

        /* Already started ? */
        if (isStarted()) {
            log.info("Proton is already started");
            return;
        }

        /* Connect reader first */
        if (!isConnected()) {
            connectReader();

            /* Connection successful ? */
            if (!isConnected()) {
                return;
            }
        }

        /* Read mask and flags */
        byte[] mask = new byte[] { 0, 0, 0, 0 };
        int flags =
            CAENRFIDLogicalSource.InventoryFlag.RSSI.getValue() |
            CAENRFIDLogicalSource.InventoryFlag.FRAMED.getValue() |
            CAENRFIDLogicalSource.InventoryFlag.CONTINUOS.getValue();

        /* Start continuous read */
        try {

            /* Start inventory on all requested sources */
            for (String sourceName : sourceNames) {
                CAENRFIDLogicalSource source = reader.GetSource(sourceName);
                source.SetReadCycle(0);
                if (!source.EventInventoryTag(mask, (short) 0, (short) 0, (short)flags)) {
                    log.error("Could not start continuous read on " + sourceName);
                }
            }

        } catch (Exception e) {
            log.error("Failed to start continuous read: " + e.getMessage());
            e.printStackTrace();
            setStopped();
            return;
        }

        /* Started */
        log.info("CAEN RFID Proton reader started");
        setStarted();
    }

    @Override
    public void stop() {
        log.debug("Stopping CAEN RFID Proton reader...");

        /* Already stopped ? */
        if (!isStarted()) {
            log.info("Proton is already stopped");
            return;
        }

        /* Stop continuous read */
        try {
            reader.InventoryAbort();
        } catch (Exception e) {
            log.error("Failed to stop continuous read: " + e.getMessage());
            e.printStackTrace();
        }

        /* Stopped */
        log.info("CAEN RFID Proton reader stopped");
        setStopped();
    }

    /**
     * Connect reader
     */
    @Override
    public void connectReader() {

        /* Try to connect */
        try {
            log.info("Connecting to " + ip + " TCP port " + port);
            reader.Connect(CAENRFIDPort.CAENRFID_TCP, ip + ":" + port);
        } catch (Exception e) {
            log.error("Failed to connect: " + e.getMessage());
            setDisconnected();
            return;
        }

        /* Try to get info */
        try {
            CAENRFIDReaderInfo info = reader.GetReaderInfo();
            String fw = reader.GetFirmwareRelease();
            log.info("Model " + info.GetModel() + ", serial " + info.GetSerialNumber() + ", firmware " + fw);
        } catch (Exception e) {
            log.error("Failed to get reader info: " + e.getMessage());
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
            log.info("Disconnecting from " + ip + " TCP port " + port);
            reader.Disconnect();
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
    public void CAENRFIDTagNotify(CAENRFIDEvent caenrfidEvent) {
        List<Tag> tags = new LinkedList<Tag>();

        for (Object obj : caenrfidEvent.getData()) {
            if (obj instanceof CAENRFIDNotify) {
                CAENRFIDNotify notify = (CAENRFIDNotify) obj;

                Tag tag = new Tag();
                tag.setReader(getName());
                tag.setOrigin(notify.getTagSource());
                tag.addTrace(notify.getReadPoint());
                tag.setTimestamp(notify.getDate().toInstant().toEpochMilli());

                /* ID's */
                BigInteger bin = new BigInteger(notify.getTagID());
                tag.setTagID(notify.getTagID());
                tag.setTagAsHex(bin.toString(16));

                String binString = bin.toString(2);
                if (binString.startsWith("1") && (binString.length() < 96)) {
                    binString = "00" + binString; /* TODO What if it's still not enough for 96 bits ? */
                }
                tag.setTagAsBinary(binString);

                /* Use conversion to get more data */
                try {
                    String pureID = TagHelper.convert_to_PURE_IDENTITY(null, null, null, tag.getTagAsBinary());
                    tag.setTagIDAsPureURI(pureID);

                    String epc_tag = TagHelper.convert_to_TAG_ENCODING(null, null, null, tag.getTagAsBinary(), tdt);
                    tag.setTagIDAsTagURI(epc_tag);

                } catch (Exception e) {
                    log.debug("Tag decoding error: " + e.getMessage());
                }

                tags.add(tag);
            }
        }

        if (tags.size() > 0) {
            addTags(tags);
        }
    }
}
