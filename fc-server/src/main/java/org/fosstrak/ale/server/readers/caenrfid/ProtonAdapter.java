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

import static org.fosstrak.ale.util.HexUtil.byteArrayToBinString;
import static org.fosstrak.ale.util.HexUtil.byteArrayToHexString;

/**
 * CAEN RFID Proton reader adapter
 */
public class ProtonAdapter extends BaseReader implements CAENRFIDEventListener {

    /** Logger */
    private static final Logger log = Logger.getLogger(ProtonAdapter.class);

    /** Variables */
    private String ip;
    private int port = 1000;
    private String source = "Source_0";
    private boolean readTID = false;
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

        /* Get port (optional) */
        String portString = logicalReaderProperties.get("Port");
        if ((portString == null) || (portString.isEmpty())) {
            log.info("Reader port is not defined, using default: " + port);
        } else {
            port = Integer.parseInt(portString);
        }

        /* Get source (optional) */
        String sourceName = logicalReaderProperties.get("Source");
        if ((sourceName == null) || (sourceName.isEmpty())) {
            log.info("No source defined, using default: " + source);
        } else {
            try {
                reader.GetSource(sourceName); // Ignore return value
                source = sourceName; // Use it, if made so far
            } catch (CAENRFIDException e) {
                log.error("Invalid source (" + sourceName + "), using default: " + source);
            }
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
        log.info("Starting CAEN RFID Proton reader...");

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
                log.error("Failed to started");
                return;
            }
        }

        try {
            reader.SetIODIRECTION(0x000C);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("IO value is " + reader.GetIO());
        } catch (CAENRFIDException e) {
            e.printStackTrace();
        }

        /* Read mask and flags */
        byte[] mask = new byte[] { 0, 0, 0, 0 };
        int flags =
            CAENRFIDLogicalSource.InventoryFlag.RSSI.getValue() |
            CAENRFIDLogicalSource.InventoryFlag.FRAMED.getValue() |
            CAENRFIDLogicalSource.InventoryFlag.CONTINUOS.getValue();

        if (readTID) {
            flags |= CAENRFIDLogicalSource.InventoryFlag.TID_READING.getValue();
        }

        /* Start continuous read */
        try {

            /* Start inventory on requested source */
            CAENRFIDLogicalSource logicalSource = reader.GetSource(source);
            logicalSource.SetReadCycle(0);
            if (!logicalSource.EventInventoryTag(mask, (short) 0, (short) 0, (short)flags)) {
                log.error("Could not start continuous read on " + source);
            }

            /* Note: EventInventoryTag would block if called second time (e.g. when want to read multiple sources).
                     and that's why here currently support for only one source at time. */

        } catch (Exception e) {
            log.error("Failed to start continuous read: " + e.getMessage());
            e.printStackTrace();
            setStopped();
            return;
        }

        /* Started */
        log.info("CAEN RFID Proton reader started on source " + source);
        setStarted();
    }

    @Override
    public void stop() {
        log.info("Stopping CAEN RFID Proton reader...");

        /* Already stopped ? */
        if (!isStarted()) {
            log.info("Proton is already stopped");
            return;
        }

        /* Stop continuous read */
        try {
            reader.InventoryAbort();
        } catch (Exception e) {
            log.error("Failed to abort continuous read: " + e.getMessage());
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
            log.info("Connecting to " + ip + " TCP port " + port + "...");
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
            log.info("Sources:");

            for (CAENRFIDLogicalSource source : reader.GetSources()) {
                log.info("  " + source.GetName());
            }
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
            log.info("Disconnecting from " + ip + " TCP port " + port + "...");
            reader.Disconnect(); // This will also abort inventory, if that happens to be running meanwhile
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
                tag.setTagID(notify.getTagID());
                tag.setTagAsHex(byteArrayToHexString(notify.getTagID()));
                tag.setTagAsBinary(byteArrayToBinString(notify.getTagID()));

                /* TID ? */
                if (readTID && (notify.getTID() != null)) {
                    tag.setTidBank(byteArrayToHexString(notify.getTID()));
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
        }

        if (tags.size() > 0) {
            addTags(tags);
        }
    }
}
