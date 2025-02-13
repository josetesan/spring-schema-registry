package org.springframework.schemaregistry.serializer;


import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDecoder;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import kafka.utils.VerifiableProperties;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.util.Utf8;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.schemaregistry.core.SslSocketFactoryConfig;
import org.springframework.schemaregistry.deserializer.SpecificKafkaAvroDeserializer;
import org.springframework.util.ResourceUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificKafkaAvroSerializerTest {

  private Map<String, String> props;

  private MockSchemaRegistryClient schemaRegistry;

  private KafkaAvroDecoder avroDecoder;

  private Serializer<Object> serializer;

  private Deserializer<Object> deserializer;

  private final static String TOPIC = "xpto";

  @BeforeEach
  public void setUp() {
    schemaRegistry = new MockSchemaRegistryClient();

    props = new HashMap<>();
    props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");

    final Properties defaultConfig = new Properties();
    defaultConfig.putAll(props);

    avroDecoder = new KafkaAvroDecoder(schemaRegistry, new VerifiableProperties(defaultConfig));

    serializer = new SpecificKafkaAvroSerializer(schemaRegistry, props);

    deserializer = new SpecificKafkaAvroDeserializer(schemaRegistry, props);
  }

  @Test
  public void testWrapperKafkaAvroSerializer() throws IOException {

    byte[] bytes;

    final IndexedRecord avroRecord = createAvroRecord();
    bytes = serializer.serialize(TOPIC, avroRecord);
    assertEquals(avroRecord, deserializer.deserialize(TOPIC, bytes));
    assertEquals(avroRecord, avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, null);
    assertThat(deserializer.deserialize(TOPIC, bytes)).isNull();
    assertThat(avroDecoder.fromBytes(bytes)).isNull();

    bytes = serializer.serialize(TOPIC, true);
    assertThat(deserializer.deserialize(TOPIC, bytes)).isEqualTo(Boolean.TRUE);
    assertThat(avroDecoder.fromBytes(bytes)).isEqualTo(Boolean.TRUE);

    bytes = serializer.serialize(TOPIC, 123);
    assertEquals(123, deserializer.deserialize(TOPIC, bytes));
    assertEquals(123, avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, 345L);
    assertEquals(345l, deserializer.deserialize(TOPIC, bytes));
    assertEquals(345l, avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, 1.23f);
    assertEquals(1.23f, deserializer.deserialize(TOPIC, bytes));
    assertEquals(1.23f, avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, 2.34d);
    assertEquals(2.34, deserializer.deserialize(TOPIC, bytes));
    assertEquals(2.34, avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, "abc");
    assertEquals("abc", deserializer.deserialize(TOPIC, bytes));
    assertEquals("abc", avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, "abc".getBytes());
    assertArrayEquals("abc".getBytes(), (byte[]) deserializer.deserialize(TOPIC, bytes));
    assertArrayEquals("abc".getBytes(), (byte[]) avroDecoder.fromBytes(bytes));

    bytes = serializer.serialize(TOPIC, new Utf8("abc"));
    assertEquals("abc", deserializer.deserialize(TOPIC, bytes));
    assertEquals("abc", avroDecoder.fromBytes(bytes));

    serializer.close();
    deserializer.close();
  }

  @Test
  public void testKafkaAvroSerializerConfigureWithSSL() throws IOException, NoSuchAlgorithmException {
    final SslSocketFactoryConfig properties = new SslSocketFactoryConfig();
    properties.setProtocol("SSL");
    properties.setKeyPassword("changeit");
    properties.setKeyStoreLocation("classpath:keystore-test.jks");
    properties.setKeyStorePassword("changeit");
    properties.setKeyManagerAlgorithm("SunX509");
    properties.setKeyStoreType("JKS");
    properties.setTrustStoreLocation("classpath:truststore-test.jks");
    properties.setTrustStorePassword("changeit");
    properties.setTrustManagerAlgorithm("SunX509");
    properties.setTrustStoreType("JKS");
    properties.setProvider(SSLContext.getDefault().getProvider());
    properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
    properties.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

    try (final Serializer<Object> serializer = new SpecificKafkaAvroSerializer()) {
      serializer.configure(properties, false);
      assertThat( serializer.serialize(TOPIC, null)).isNull();
    }
  }

  @Test
  public void testKafkaAvroSerializerConfigureWithOutSsl() throws IOException, NoSuchAlgorithmException {
    final SslSocketFactoryConfig properties = new SslSocketFactoryConfig();
    properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
    properties.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

    try (final Serializer<Object> serializer = new SpecificKafkaAvroSerializer()) {
      serializer.configure(properties, false);
      assertThat( serializer.serialize(TOPIC, null)).isNull();
    }
  }

  @Test
  public void testKafkaAvroSerializerWithoutAutoRegister() throws IOException {
    final Map<String, Object> configs = new HashMap<>();
    configs.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
    configs.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

    serializer.configure(configs, false);
    final IndexedRecord avroRecord = createAvroRecord();
    assertThatThrownBy(() -> serializer.serialize(TOPIC, avroRecord))
        .isInstanceOf(SerializationException.class);
  }

  @Test
  public void testKafkaAvroSerializerWithPreRegistered() throws IOException, RestClientException {
    final Map<String, Object> configs = new HashMap<>();
    configs.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
    configs.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

    serializer.configure(configs, false);
    final IndexedRecord avroRecord = createAvroRecord();
    AvroSchema avroSchema = new AvroSchema(avroRecord.getSchema());
    schemaRegistry.register(TOPIC + "-value", avroSchema);
    final byte[] bytes = serializer.serialize(TOPIC, avroRecord);

    assertEquals(avroRecord, deserializer.deserialize(TOPIC, bytes));
    assertEquals(avroRecord, avroDecoder.fromBytes(bytes));
  }

  @Test
  public void testNull() {
    try (final Serializer<Object> nullAvroSerializer = new SpecificKafkaAvroSerializer(null)) {
      assertThat(nullAvroSerializer.serialize("test", null)).isNull();
    }
  }

  @Test
  public void testKafkaAvroSerializerSpecificRecordWithPrimitives() {
    final Map<String, Object> configs = new HashMap<>();
    configs.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
    configs.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");

    final Properties defaultConfig = new Properties();
    defaultConfig.putAll(props);

    final Deserializer<Object> specificAvroDeserializer = new SpecificKafkaAvroDeserializer(schemaRegistry, configs);
    final KafkaAvroDecoder specificAvroDecoder = new KafkaAvroDecoder(schemaRegistry, new VerifiableProperties(defaultConfig));

    final String message = "testKafkaAvroSerializerSpecificRecordWithPrimitives";
    final byte[] bytes = serializer.serialize(TOPIC, message);

    Object obj;

    obj = avroDecoder.fromBytes(bytes);
    assertTrue(String.class.isInstance(obj),"Returned object should be a String");

    obj = specificAvroDecoder.fromBytes(bytes);
    assertTrue(String.class.isInstance(obj),"Returned object should be a String");
    assertEquals(message, obj);

    obj = specificAvroDeserializer.deserialize(TOPIC, bytes);
    assertTrue( String.class.isInstance(obj),"Returned object should be a String");
    assertEquals(message, obj);

    specificAvroDeserializer.close();
  }

  @Test
  public void testSchemasPerSubject() {
    final HashMap<String, String> props = new HashMap<>();
    props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
    props.put(AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_CONFIG, "5");
    serializer.configure(props, false);
    assertThat(serializer.serialize(TOPIC, null)).isNull();
  }

  private IndexedRecord createAvroRecord() throws IOException {
    final File userSchema = ResourceUtils.getFile("classpath:avro/user.avsc");
    final Parser parser = new Parser();
    final Schema schema = parser.parse(userSchema);
    final GenericRecord avroRecord = new GenericData.Record(schema);
    avroRecord.put("name", "Test user");
    return avroRecord;
  }

}
