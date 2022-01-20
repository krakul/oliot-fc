package org.fosstrak.ale.server.readers.sensthys;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Hashtable;
import java.util.List;

import com.unboundid.util.args.ArgumentException;
import org.apache.log4j.Logger;
import org.fosstrak.ale.exception.ImplementationException;
import org.fosstrak.ale.exception.ValidationException;
import org.fosstrak.ale.server.Tag;
import org.fosstrak.ale.server.readers.BaseReader;
import org.fosstrak.ale.server.readers.LogicalReader;
import org.fosstrak.ale.server.util.TagHelper;
import org.fosstrak.ale.xsd.ale.epcglobal.CCOpSpec;
import org.fosstrak.ale.xsd.ale.epcglobal.CCSpec;
import org.fosstrak.ale.xsd.ale.epcglobal.LRSpec;
import org.fosstrak.hal.HardwareException;
import org.fosstrak.hal.Observation;

import RFIDReader.OnTagReadResponse;
import RFIDReader.RFIDReader;
import RFIDReader.TagReadInfo;
import org.fosstrak.tdt.TDTEngine;

/**
 * SensThys SensX EXTREME reader adapter
 */
public class SensXExtremeAdaptor extends BaseReader implements OnTagReadResponse {

    /** Logger */
    private static final Logger log = Logger.getLogger(SensXExtremeAdaptor.class);

    /** Date & time format which the reader accepts */
    private static final DateTimeFormatter readerDateTimeFormat = DateTimeFormatter.ofPattern("YYYY-MM-DD'T'hh:mm:ss");

    /** Variables */
    private RFIDReader reader = null;
    private TDTEngine tdt = null;

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
        String ip = logicalReaderProperties.get("IP");
        if ((ip == null) || (ip.isEmpty()))
        {
            log.error("Reader IP is not defined.");
            return;
        }

        /* Get port */
        String portString = logicalReaderProperties.get("Port");
        if ((portString == null) || (portString.isEmpty())) {
            log.error("Reader port is not defined.");
            return;
        }

        int port;
        port = Integer.parseInt(portString);

        /* Create reader - this is just constructor call, which doesn't connect yet */
        log.debug("Create new SensThys SensX EXTREME reader, IP " + ip + ", port " + port);
        reader = new RFIDReader(ip, port);

        /* Set this class as a read callback handler */
        reader.SetTagReadCallback(this);

        /* Create TDT engine */
        tdt = TagHelper.getTDTEngine();
    }

    /**
     * updates a reader according the specified LRSpec.
     * @param spec LRSpec for the reader
     * @throws ImplementationException whenever an internal error occurs
     */
    @Override
    public void update(LRSpec spec) throws ImplementationException {

    }

    /**
     * Triggers the identification of all tags that are currently available
     * on the reader.
     * @param readPointNames the readers/sources that have to be polled
     * @return a set of Observations
     * @throws HardwareException whenever an internal hardware error occurs (as reader not available...)
     */
    @Override
    public Observation[] identify(String[] readPointNames) throws HardwareException {
        return new Observation[0];
    }

    /**
     * Start reader.
     * Connects the reader unless it's not already connected.
     */
    @Override
    public void start() {
        log.debug("Starting SensThys SensX EXTREME reader...");

        /* Already started ? */
        if (isStarted()) {
            log.info("SensX is already started");
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

        /* Set UTC date and time of reader */
        OffsetDateTime utcTimestamp = Instant.now().atOffset(ZoneOffset.UTC);
        String utcTimestampString = utcTimestamp.format(readerDateTimeFormat);
        log.debug("Setting reader date-time to UTC timestamp: " + utcTimestampString);

        try {
            reader.SetReaderDateAndTime(utcTimestampString);
        } catch (Exception e) {
            log.error("Failed to set date-time of reader: " + e.getMessage());
            e.printStackTrace();
            setStopped();
            return;
        }

        /* Start continuous read */
        try {
            reader.StartContinuousRead();
        } catch (Exception e) {
            log.error("Failed to start continuous read: " + e.getMessage());
            e.printStackTrace();
            setStopped();
            return;
        }

        /* Started */
        log.info("SensX SensThys EXTREME reader started");
        setStarted();
    }

    /**
     * Stop reader.
     */
    @Override
    public void stop() {
        log.debug("Stopping SensThys SensX EXTREME reader...");

        /* Already stopped ? */
        if (!isStarted()) {
            log.info("SensX is already stopped");
            return;
        }

        /* Stop continuous read */
        try {
            reader.StopContinuousRead();
        } catch (Exception e) {
            log.error("Failed to stop continuous read: " + e.getMessage());
            e.printStackTrace();
        }

        /* Stopped */
        log.info("SensX SensThys EXTREME reader stopped");
        setStopped();
    }

    /**
     * Connect reader.
     */
    @Override
    public void connectReader() {
        String hwId, fwId, serial, name;

        /* There is no public "connect" function in SDK, we use some read functions to establish and check the connection */

        try {
            hwId = reader.GetHardwareId();
        } catch (Exception e) {
            log.error("Failed to get hardware ID: " + e.getMessage());
            e.printStackTrace();
            setDisconnected();
            return;
        }

        try {
            fwId = reader.GetFirmwareId();
        } catch (Exception e) {
            log.error("Failed to get firmware ID: " + e.getMessage());
            e.printStackTrace();
            setDisconnected();
            return;
        }

        try {
            serial = reader.GetSerialNumber();
        } catch (Exception e) {
            log.error("Failed to get serial number: " + e.getMessage());
            e.printStackTrace();
            setDisconnected();
            return;
        }

        try {
            name = reader.GetReaderName();
        } catch (Exception e) {
            log.error("Failed to get reader name: " + e.getMessage());
            e.printStackTrace();
            setDisconnected();
            return;
        }

        /* Print info */
        log.info("Connected to SensThys SensX EXTREME reader.");
        log.info("  Hardware ID:   " + hwId);
        log.info("  Firmware ID:   " + fwId);
        log.info("  Serial number: " + serial);
        log.info("  Reader name:   " + name);

        /* Consider reader as connected */
        setConnected();
    }

    /**
     * Disconnect reader.
     * Stops the reader also, if it's started.
     */
    @Override
    public void disconnectReader() {
        /* Some Fosstrak/Oliot code wants to disconnect reader without stopping it first, so do it now,
         * otherwise the reader might be in reading mode and won't stop */
        if (isStarted()) {
            stop();
        }

        /* Shutdown just closes connection */
        reader.Shutdown();
        setDisconnected();
    }

    @Override
    public void ADDACCESSSPECfromCCSpec(CCSpec ccspec, Hashtable<Integer, CCOpSpec> OpSpecTable) {
        // TODO Implement
    }

    @Override
    public void DELETEACCESSSPEC() {
        // TODO Implement
    }

    @Override
    public void recoveryACCESSSPEC3() {
        // TODO Implement
    }

    /**
     * Tag reading feedback handler.
     * @param readInfo
     */
    public void handleTagRead(TagReadInfo readInfo) {
        Tag tag = new Tag();
        tag.setReader(getName());
        tag.setOrigin(getName());
        tag.addTrace("Antenna " + readInfo.antennaNumber);
        tag.setTimestamp(readInfo.timeStamp.toEpochSecond(ZoneOffset.UTC)); // Get UTC timestamp

        /* Get hexadecimal EPC value.
         * SensX sends 11-22-33-AA-BB-CC.... first get rid of dashes. */
        String hex = readInfo.EPC.replace("-", "").toLowerCase();
        tag.setTagAsHex(hex);

        /* Get binary value */
        BigInteger bin = new BigInteger(hex, 16);
        String binString = bin.toString(2);
        if (binString.startsWith("1") && (binString.length() < 96)) {
            binString = "00" + binString; /* TODO What if it's still not enough for 96 bits ? */
        }
        tag.setTagAsBinary(binString);
        tag.setTagID(binString.getBytes());

        /* Use conversion to get more data */
        try {
            String pureID = TagHelper.convert_to_PURE_IDENTITY(null, null, null, tag.getTagAsBinary());
            tag.setTagIDAsPureURI(pureID);

            String epc_tag = TagHelper.convert_to_TAG_ENCODING(null, null, null, tag.getTagAsBinary(), tdt);
            tag.setTagIDAsTagURI(epc_tag);

        } catch (Exception e) {
            /* Treat it as "debug" event because invalid/unprogrammed tags should not be treated as errors */
            log.debug("Tag decoding error: " + e.getMessage());
        }

        addTag(tag);
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
}
