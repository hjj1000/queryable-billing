package com.tngtech.qb;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.runtime.query.QueryableStateClient;
import org.apache.flink.runtime.query.netty.message.KvStateRequestSerializer;
import org.apache.flink.runtime.state.VoidNamespace;
import org.apache.flink.runtime.state.VoidNamespaceSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class FlinkStateQueryService {
  private final QueryableStateClient client;
  private final JobID jobId;

  public FlinkStateQueryService(
      @Value("${flink.jobIdHex}") String jobIdHex,
      @Value("${flink.configDir}") String flinkConfigDir)
      throws Exception {
    jobId = JobID.fromHexString(jobIdHex);
    client = new QueryableStateClient(GlobalConfiguration.loadConfiguration(flinkConfigDir));
  }

  BillableEvent findOne(String customer) throws Exception {
    final Future<byte[]> stateFuture =
        client.getKvState(
            jobId, Constants.LATEST_EVENT_STATE_NAME, customer.hashCode(), serialize(customer));
    final byte[] serializedResult =
        Await.result(stateFuture, new FiniteDuration(10, TimeUnit.SECONDS));
    return deserialize(serializedResult);
  }

  Set<String> findAllCustomers() throws Exception {
    final Future<byte[]> stateFuture =
        client.getKvState(
            jobId,
            Constants.CUSTOMERS_STATE_NAME,
            Constants.CUSTOMERS_KEY.hashCode(),
            serialize(Constants.CUSTOMERS_KEY));
    final byte[] serializedResult =
        Await.result(stateFuture, new FiniteDuration(10, TimeUnit.SECONDS));
    return deserializeSet(serializedResult);
  }

  private byte[] serialize(String key) throws IOException {
    TypeSerializer<String> keySerializer =
        TypeInformation.of(new TypeHint<String>() {}).createSerializer(null);
    return KvStateRequestSerializer.serializeKeyAndNamespace(
        key, keySerializer, VoidNamespace.INSTANCE, VoidNamespaceSerializer.INSTANCE);
  }

  private byte[] serialize(Integer key) throws IOException {
    TypeSerializer<Integer> keySerializer =
        TypeInformation.of(new TypeHint<Integer>() {}).createSerializer(null);
    return KvStateRequestSerializer.serializeKeyAndNamespace(
        key, keySerializer, VoidNamespace.INSTANCE, VoidNamespaceSerializer.INSTANCE);
  }

  private BillableEvent deserialize(byte[] serializedResult) throws IOException {
    return KvStateRequestSerializer.deserializeValue(
        serializedResult,
        TypeInformation.of(new TypeHint<BillableEvent>() {})
            .createSerializer(new ExecutionConfig()));
  }

  private Set<String> deserializeSet(byte[] serializedResult) throws IOException {
    return KvStateRequestSerializer.deserializeValue(
        serializedResult,
        TypeInformation.of(new TypeHint<Set<String>>() {}).createSerializer(new ExecutionConfig()));
  }
}
