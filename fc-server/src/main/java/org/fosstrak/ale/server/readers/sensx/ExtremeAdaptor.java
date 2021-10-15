package org.fosstrak.ale.server.readers.sensx;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Hashtable;
import java.util.List;

import com.unboundid.util.args.ArgumentException;
import org.apache.log4j.Logger;
import org.fosstrak.ale.exception.ImplementationException;
import org.fosstrak.ale.server.Tag;
import org.fosstrak.ale.server.readers.BaseReader;
import org.fosstrak.ale.xsd.ale.epcglobal.CCOpSpec;
import org.fosstrak.ale.xsd.ale.epcglobal.CCSpec;
import org.fosstrak.ale.xsd.ale.epcglobal.LRSpec;
import org.fosstrak.hal.HardwareException;
import org.fosstrak.hal.Observation;

import RFIDReader.OnTagReadResponse;
import RFIDReader.RFIDReader;
import RFIDReader.TagReadInfo;

/**
 * SensThys SensX EXTREME reader adapter
 */
public class ExtremeAdaptor extends BaseReader implements OnTagReadResponse {

    /** Logger */
    private static final Logger log = Logger.getLogger(ExtremeAdaptor.class);

    /** Date & time format which the reader accepts */
    private static final DateTimeFormatter readerDateTimeFormat = DateTimeFormatter.ofPattern("YYYY-MM-DDThh:mm:ss");

    /** Reader */
    private RFIDReader reader = null;

    /**
     * initializes a LLRPAdaptor. this method must be called before the Adaptor can
     * be used.
     * @param name the name for the reader encapsulated by this adaptor.
     * @param spec the specification that describes the current reader.
     * @throws ImplementationException whenever an internal error occurs.

     */
    public void initialize(String name, LRSpec spec) throws ImplementationException {
        super.initialize(name, spec);

        /* Dispose previous reader */
        reader = null;

        /* Check arguments */
        if ((name == null) || (spec == null)) {
            log.error("Reader name or LRSpec is null.");
            throw new ImplementationException("Reader name or LRSpec is null.");
        }

        /* Get IP */
        String ip = logicalReaderProperties.get("ip");
        if ((ip == null) || (ip.isEmpty()))
        {
            log.error("Reader IP is not defined.");
            return;
        }

        /* Get port */
        String portString = logicalReaderProperties.get("port");
        if ((portString == null) || (portString.isEmpty())) {
            log.error("Reader port is not defined.");
            return;
        }

        int port;
        port = Integer.parseInt(portString);

        /* Create reader */
        log.debug("Create new EXTREME reader, IP " + ip + ", port " + port);
        reader = new RFIDReader(ip, port);
    }

    @Override
    public void addTag(Tag tag) {
        setChanged();
        notifyObservers(tag);
    }

    @Override
    public void addTags(List<Tag> tags) {
        setChanged();
        notifyObservers(tags);
    }

    /**
     * Start reader.
     * If reader is not yet connected, then connects before start command is sent.
     * @throws ImplementationException
     */
    @Override
    public void start() {

        /* Connect reader first */
        if (isConnected()) {
            connectReader();
        }

        /* No point of starting if not connected */
        if (!isConnected())
        {
            setStopped();
            return;
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
        }

        /* Set this class as a read callback handler */
        reader.SetTagReadCallback(this);

        /* Start continuous read */
        try {
            reader.StartContinuousRead();
        } catch (Exception e) {
            log.error("Failed to start continuous read: " + e.getMessage());
            e.printStackTrace();
            setStopped();
        }

        /* Started */
        log.info("SensX EXTREME reader started");
        setStarted();
    }

    /**
     * Stop reader.
     * This is only possible if reader is already connected.
     * Note: This function does not disconnect reader.
     */
    @Override
    public void stop() {

        /* Can't stop if not connected */
        if (!isConnected())
        {
            log.error("Can't stop reader if it's not connected.");
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
        log.info("SensX EXTREME reader stopped");
        setStopped();
    }

    /**
     * Connect reader.
     * @throws ImplementationException
     */
    @Override
    public void connectReader() {

        /* Can't connect if not initialized */
        if (reader == null)
        {
            log.error("Reader not initialized");
            setDisconnected();
            return;
        }

        log.debug("Connecting SensX EXTREME reader...");

        /* There is no public connect command, we use some read functions to establish and check the connection */
        String hwId, fwId, serial, name;

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
        log.info("Connected to SensX EXTREME reader.");
        log.info("  Hardware ID:   " + hwId);
        log.info("  Firmware ID:   " + fwId);
        log.info("  Serial number: " + serial);
        log.info("  Reader name:   " + name);

        /* Consider reader as connected */
        setConnected();
    }

    /**
     * Disconnect reader.
     * Note: This function does not stop the reader.
     */
    @Override
    public void disconnectReader() throws ImplementationException {

        /* Can't connect if not initialized */
        if (reader == null)
        {
            log.error("Reader not initialized");
            setDisconnected();
            return;
        }

        log.debug("Disconnecting SensX EXTREME reader...");

        /* Shutdown just closes connection */
        reader.Shutdown();

        /* Print status */
        log.info("Disconnected from SensX EXTREME reader.");
        setDisconnected();
    }

    @Override
    public void update(LRSpec spec) throws ImplementationException {
        // TODO Implement
    }

    @Override
    public Observation[] identify(String[] readPointNames) throws HardwareException {
        // TODO Implement
        return new Observation[0];
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
    public void handleTagRead(TagReadInfo readInfo)
    {
        Tag tag = new Tag();
        tag.setReader(getName());
        tag.setOrigin(getName());
        tag.addTrace("Antenna " + readInfo.antennaNumber);
        tag.setTagIDAsPureURI(readInfo.EPC); // TODO Not sure it's correct, investigate
        tag.setTimestamp(readInfo.timeStamp.toEpochSecond(ZoneOffset.UTC)); // Get UTC timestamp

        addTag(tag);
    }
}
