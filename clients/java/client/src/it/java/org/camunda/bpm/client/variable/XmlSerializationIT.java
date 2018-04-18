/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.client.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.client.util.ProcessModels.EXTERNAL_TASK_TOPIC_FOO;
import static org.camunda.bpm.client.util.ProcessModels.LOCK_DURATION;
import static org.camunda.bpm.client.util.ProcessModels.TWO_EXTERNAL_TASK_PROCESS;
import static org.camunda.bpm.engine.variable.type.ValueType.OBJECT;
import static org.camunda.spin.DataFormats.XML_DATAFORMAT_NAME;

import java.util.Arrays;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.dto.ProcessDefinitionDto;
import org.camunda.bpm.client.dto.ProcessInstanceDto;
import org.camunda.bpm.client.rule.ClientRule;
import org.camunda.bpm.client.rule.EngineRule;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.util.RecordingExternalTaskHandler;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.spin.Spin;
import org.camunda.spin.SpinList;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class XmlSerializationIT {

  protected static final String VARIABLE_NAME_XML = "xmlVariable";

  protected static final XmlSerializable VARIABLE_VALUE_XML_DESERIALIZED = new XmlSerializable("a String", 42, true);
  protected static final XmlSerializables VARIABLE_VALUE_XML_LIST_DESERIALIZED = new XmlSerializables(Arrays.asList(VARIABLE_VALUE_XML_DESERIALIZED, VARIABLE_VALUE_XML_DESERIALIZED));

  protected static final String VARIABLE_VALUE_XML_SERIALIZED = VARIABLE_VALUE_XML_DESERIALIZED.toExpectedXmlString();
  protected static final String VARIABLE_VALUE_XML_LIST_SERIALIZED = VARIABLE_VALUE_XML_LIST_DESERIALIZED.toExpectedXmlString();

  protected static final ObjectValue VARIABLE_VALUE_XML_OBJECT_VALUE = Variables
      .serializedObjectValue(VARIABLE_VALUE_XML_DESERIALIZED.toExpectedXmlString())
      .objectTypeName(XmlSerializable.class.getName())
      .serializationDataFormat(XML_DATAFORMAT_NAME)
      .create();

  protected static final ObjectValue VARIABLE_VALUE_XML_LIST_OBJECT_VALUE = Variables
      .serializedObjectValue(VARIABLE_VALUE_XML_LIST_SERIALIZED)
      .objectTypeName(XmlSerializables.class.getName())
      .serializationDataFormat(XML_DATAFORMAT_NAME)
      .create();

  protected ClientRule clientRule = new ClientRule();
  protected EngineRule engineRule = new EngineRule();
  protected ExpectedException thrown = ExpectedException.none();

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(clientRule).around(thrown);

  protected ExternalTaskClient client;

  protected ProcessDefinitionDto processDefinition;
  protected ProcessInstanceDto processInstance;

  protected RecordingExternalTaskHandler handler = new RecordingExternalTaskHandler();

  @Before
  public void setup() throws Exception {
    client = clientRule.client();
    handler.clear();
    processDefinition = engineRule.deploy(TWO_EXTERNAL_TASK_PROCESS).get(0);
  }

  @Test
  public void shouldGetDeserializedXml() {
    // given
    engineRule.startProcessInstance(processDefinition.getId(), VARIABLE_NAME_XML, VARIABLE_VALUE_XML_OBJECT_VALUE);

    // when
    client.subscribe(EXTERNAL_TASK_TOPIC_FOO)
      .lockDuration(LOCK_DURATION)
      .handler(handler)
      .open();

    // then
    clientRule.waitForFetchAndLockUntil(() -> !handler.getHandledTasks().isEmpty());

    ExternalTask task = handler.getHandledTasks().get(0);

    XmlSerializable variableValue = task.getVariable(VARIABLE_NAME_XML);
    assertThat(variableValue).isEqualTo(VARIABLE_VALUE_XML_DESERIALIZED);
  }

  @Test
  public void shouldGetTypedDeserializedXml() {
    // given
    engineRule.startProcessInstance(processDefinition.getId(), VARIABLE_NAME_XML, VARIABLE_VALUE_XML_OBJECT_VALUE);

    // when
    client.subscribe(EXTERNAL_TASK_TOPIC_FOO)
      .lockDuration(LOCK_DURATION)
      .handler(handler)
      .open();

    // then
    clientRule.waitForFetchAndLockUntil(() -> !handler.getHandledTasks().isEmpty());

    ExternalTask task = handler.getHandledTasks().get(0);

    ObjectValue typedValue = task.getVariableTyped(VARIABLE_NAME_XML);
    assertThat(typedValue.getValue()).isEqualTo(VARIABLE_VALUE_XML_DESERIALIZED);
    assertThat(typedValue.getObjectTypeName()).isEqualTo(XmlSerializable.class.getName());
    assertThat(typedValue.getType()).isEqualTo(OBJECT);
    assertThat(typedValue.isDeserialized()).isTrue();
  }

  @Test
  public void shouldGetTypedSerializedXml() {
    // given
    engineRule.startProcessInstance(processDefinition.getId(), VARIABLE_NAME_XML, VARIABLE_VALUE_XML_OBJECT_VALUE);

    // when
    client.subscribe(EXTERNAL_TASK_TOPIC_FOO)
      .lockDuration(LOCK_DURATION)
      .handler(handler)
      .open();

    // then
    clientRule.waitForFetchAndLockUntil(() -> !handler.getHandledTasks().isEmpty());

    ExternalTask task = handler.getHandledTasks().get(0);

    ObjectValue typedValue = task.getVariableTyped(VARIABLE_NAME_XML, false);
    assertThat(typedValue.getObjectTypeName()).isEqualTo(XmlSerializable.class.getName());
    assertThat(typedValue.getType()).isEqualTo(OBJECT);
    assertThat(typedValue.isDeserialized()).isFalse();

    SpinXmlElement serializedValue = Spin.XML(typedValue.getValueSerialized());
    assertThat(VARIABLE_VALUE_XML_DESERIALIZED.getStringProperty()).isEqualTo(serializedValue.childElement("stringProperty").textContent());
    assertThat(VARIABLE_VALUE_XML_DESERIALIZED.getBooleanProperty()).isEqualTo(Boolean.parseBoolean(serializedValue.childElement("booleanProperty").textContent()));
    assertThat(VARIABLE_VALUE_XML_DESERIALIZED.getIntProperty()).isEqualTo(Integer.parseInt(serializedValue.childElement("intProperty").textContent()));
  }

  @Test
  public void shouldGetXmlAsList() {
    // given
    engineRule.startProcessInstance(processDefinition.getId(), VARIABLE_NAME_XML, VARIABLE_VALUE_XML_LIST_OBJECT_VALUE);

    // when
    client.subscribe(EXTERNAL_TASK_TOPIC_FOO)
      .lockDuration(LOCK_DURATION)
      .handler(handler)
      .open();

    // then
    clientRule.waitForFetchAndLockUntil(() -> !handler.getHandledTasks().isEmpty());

    ExternalTask task = handler.getHandledTasks().get(0);

    XmlSerializables variableValue = task.getVariable(VARIABLE_NAME_XML);
    assertThat(variableValue.size()).isEqualTo(2);
    assertThat(variableValue.get(0)).isEqualTo(VARIABLE_VALUE_XML_DESERIALIZED);
    assertThat(variableValue.get(1)).isEqualTo(VARIABLE_VALUE_XML_DESERIALIZED);
  }

  @Test
  public void shouldGetTypedDeserializedXmlAsList() {
    // given
    engineRule.startProcessInstance(processDefinition.getId(), VARIABLE_NAME_XML, VARIABLE_VALUE_XML_LIST_OBJECT_VALUE);

    // when
    client.subscribe(EXTERNAL_TASK_TOPIC_FOO)
      .lockDuration(LOCK_DURATION)
      .handler(handler)
      .open();

    // then
    clientRule.waitForFetchAndLockUntil(() -> !handler.getHandledTasks().isEmpty());

    ExternalTask task = handler.getHandledTasks().get(0);

    ObjectValue typedValue = task.getVariableTyped(VARIABLE_NAME_XML);
    assertThat(typedValue.getValue()).isEqualTo(VARIABLE_VALUE_XML_LIST_DESERIALIZED);
    assertThat(typedValue.getObjectTypeName()).isEqualTo(XmlSerializables.class.getName());
    assertThat(typedValue.getType()).isEqualTo(OBJECT);
    assertThat(typedValue.isDeserialized()).isTrue();
  }

  @Test
  public void shouldGetTypedSerializedXmlAsList() {
    // given
    engineRule.startProcessInstance(processDefinition.getId(), VARIABLE_NAME_XML, VARIABLE_VALUE_XML_LIST_OBJECT_VALUE);

    // when
    client.subscribe(EXTERNAL_TASK_TOPIC_FOO)
      .lockDuration(LOCK_DURATION)
      .handler(handler)
      .open();

    // then
    clientRule.waitForFetchAndLockUntil(() -> !handler.getHandledTasks().isEmpty());

    ExternalTask task = handler.getHandledTasks().get(0);

    ObjectValue typedValue = task.getVariableTyped(VARIABLE_NAME_XML, false);
    assertThat(typedValue.getObjectTypeName()).isEqualTo(XmlSerializables.class.getName());
    assertThat(typedValue.getType()).isEqualTo(OBJECT);
    assertThat(typedValue.isDeserialized()).isFalse();

    SpinXmlElement serializedValue = Spin.XML(typedValue.getValueSerialized());
    SpinList<SpinXmlElement> childElements = serializedValue.childElements();
    childElements.forEach((c) -> {
      assertThat(VARIABLE_VALUE_XML_DESERIALIZED.getStringProperty()).isEqualTo(c.childElement("stringProperty").textContent());
      assertThat(VARIABLE_VALUE_XML_DESERIALIZED.getBooleanProperty()).isEqualTo(Boolean.parseBoolean(c.childElement("booleanProperty").textContent()));
      assertThat(VARIABLE_VALUE_XML_DESERIALIZED.getIntProperty()).isEqualTo(Integer.parseInt(c.childElement("intProperty").textContent()));
    });
  }

}
