/*
 * Copyright (C) 2020 The zfoo Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.zfoo.protocol.serializer.javascript;

import com.zfoo.protocol.generate.GenerateOperation;
import com.zfoo.protocol.generate.GenerateProtocolDocument;
import com.zfoo.protocol.generate.GenerateProtocolFile;
import com.zfoo.protocol.generate.GenerateProtocolPath;
import com.zfoo.protocol.model.Pair;
import com.zfoo.protocol.registration.IProtocolRegistration;
import com.zfoo.protocol.registration.ProtocolRegistration;
import com.zfoo.protocol.registration.anno.Compatible;
import com.zfoo.protocol.serializer.reflect.*;
import com.zfoo.protocol.util.ClassUtils;
import com.zfoo.protocol.util.FileUtils;
import com.zfoo.protocol.util.IOUtils;
import com.zfoo.protocol.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.zfoo.protocol.util.FileUtils.LS;
import static com.zfoo.protocol.util.StringUtils.TAB;

/**
 * @author jaysunxiao
 * @version 3.0
 */
public abstract class GenerateJsUtils {

    private static String protocolOutputRootPath = "jsProtocol/";

    private static Map<ISerializer, IJsSerializer> jsSerializerMap;

    static {
        jsSerializerMap = new HashMap<>();
        jsSerializerMap.put(BooleanSerializer.INSTANCE, new JsBooleanSerializer());
        jsSerializerMap.put(ByteSerializer.INSTANCE, new JsByteSerializer());
        jsSerializerMap.put(ShortSerializer.INSTANCE, new JsShortSerializer());
        jsSerializerMap.put(IntSerializer.INSTANCE, new JsIntSerializer());
        jsSerializerMap.put(LongSerializer.INSTANCE, new JsLongSerializer());
        jsSerializerMap.put(FloatSerializer.INSTANCE, new JsFloatSerializer());
        jsSerializerMap.put(DoubleSerializer.INSTANCE, new JsDoubleSerializer());
        jsSerializerMap.put(CharSerializer.INSTANCE, new JsCharSerializer());
        jsSerializerMap.put(StringSerializer.INSTANCE, new JsStringSerializer());
        jsSerializerMap.put(ArraySerializer.INSTANCE, new JsArraySerializer());
        jsSerializerMap.put(ListSerializer.INSTANCE, new JsListSerializer());
        jsSerializerMap.put(SetSerializer.INSTANCE, new JsSetSerializer());
        jsSerializerMap.put(MapSerializer.INSTANCE, new JsMapSerializer());
        jsSerializerMap.put(ObjectProtocolSerializer.INSTANCE, new JsObjectProtocolSerializer());
    }

    public static IJsSerializer jsSerializer(ISerializer serializer) {
        return jsSerializerMap.get(serializer);
    }

    public static void init(GenerateOperation generateOperation) {
        protocolOutputRootPath = FileUtils.joinPath(generateOperation.getProtocolPath(), protocolOutputRootPath);

        FileUtils.deleteFile(new File(protocolOutputRootPath));
        FileUtils.createDirectory(protocolOutputRootPath);
    }

    public static void clear() {
        jsSerializerMap = null;
        protocolOutputRootPath = null;
    }

    public static void createProtocolManager(List<IProtocolRegistration> protocolList) throws IOException {
        var list = List.of("javascript/buffer/ByteBuffer.js", "javascript/buffer/long.js", "javascript/buffer/longbits.js");
        for (var fileName : list) {
            var fileInputStream = ClassUtils.getFileFromClassPath(fileName);
            var createFile = new File(StringUtils.format("{}/{}", protocolOutputRootPath, StringUtils.substringAfterFirst(fileName, "javascript/")));
            FileUtils.writeInputStreamToFile(createFile, fileInputStream);
        }

        // 生成ProtocolManager.js文件
        var protocolManagerTemplate = StringUtils.bytesToString(IOUtils.toByteArray(ClassUtils.getFileFromClassPath("javascript/ProtocolManagerTemplate.js")));

        var importBuilder = new StringBuilder();
        var initProtocolBuilder = new StringBuilder();
        for (var protocol : protocolList) {
            var protocolId = protocol.protocolId();
            var protocolName = protocol.protocolConstructor().getDeclaringClass().getSimpleName();
            var path = GenerateProtocolPath.getProtocolPath(protocol.protocolId());
            if (StringUtils.isBlank(path)) {
                importBuilder.append(StringUtils.format("import {} from './{}.js';", protocolName, protocolName)).append(LS);
            } else {
                importBuilder.append(StringUtils.format("import {} from './{}/{}.js';", protocolName, path, protocolName)).append(LS);
            }

            initProtocolBuilder.append(StringUtils.format("protocols.set({}, {});", protocolId, protocolName)).append(LS);
        }

        protocolManagerTemplate = StringUtils.format(protocolManagerTemplate, importBuilder.toString().trim(), StringUtils.EMPTY_JSON, initProtocolBuilder.toString().trim());
        FileUtils.writeStringToFile(new File(StringUtils.format("{}/{}", protocolOutputRootPath, "ProtocolManager.js")), protocolManagerTemplate);
    }

    public static void createJsProtocolFile(ProtocolRegistration registration) throws IOException {
        // 初始化index
        GenerateProtocolFile.index.set(0);

        var protocolId = registration.protocolId();
        var registrationConstructor = registration.getConstructor();
        var protocolClazzName = registrationConstructor.getDeclaringClass().getSimpleName();

        var protocolTemplate = StringUtils.bytesToString(IOUtils.toByteArray(ClassUtils.getFileFromClassPath("javascript/ProtocolTemplate.js")));

        var docTitle = docTitle(registration);
        var valueOfMethod = valueOfMethod(registration);
        var writeObject = writeObject(registration);
        var readObject = readObject(registration);

        protocolTemplate = StringUtils.format(protocolTemplate, docTitle, protocolClazzName
                , valueOfMethod.getKey().trim(), valueOfMethod.getValue().trim(), protocolClazzName, protocolId, protocolClazzName
                , writeObject.trim(), protocolClazzName, protocolClazzName, readObject.trim(), protocolClazzName);
        var protocolOutputPath = StringUtils.format("{}/{}/{}.js", protocolOutputRootPath
                , GenerateProtocolPath.getProtocolPath(protocolId), protocolClazzName);
        FileUtils.writeStringToFile(new File(protocolOutputPath), protocolTemplate);
    }

    private static String docTitle(ProtocolRegistration registration) {
        var protocolId = registration.getId();
        var protocolDocument = GenerateProtocolDocument.getProtocolDocument(protocolId);
        var docTitle = protocolDocument.getKey();
        return docTitle;
    }

    private static Pair<String, String> valueOfMethod(ProtocolRegistration registration) {
        var protocolId = registration.getId();
        var fields = registration.getFields();

        var fieldValueOf = StringUtils.joinWith(", ", Arrays.stream(fields).map(it -> it.getName()).collect(Collectors.toList()).toArray());
        var fieldDefinitionBuilder = new StringBuilder();

        var protocolDocument = GenerateProtocolDocument.getProtocolDocument(protocolId);
        var docFieldMap = protocolDocument.getValue();
        for (var field : fields) {
            var propertyName = field.getName();
            // 生成注释
            var doc = docFieldMap.get(propertyName);
            if (StringUtils.isNotBlank(doc)) {
                Arrays.stream(doc.split(LS)).forEach(it -> fieldDefinitionBuilder.append(TAB).append(it).append(LS));
            }

            fieldDefinitionBuilder.append(TAB)
                    .append(StringUtils.format("this.{} = {};", propertyName, propertyName))
                    .append(" // ").append(field.getGenericType().getTypeName())// 生成类型的注释
                    .append(LS);
        }
        return new Pair<>(fieldValueOf, fieldDefinitionBuilder.toString());
    }

    private static String writeObject(ProtocolRegistration registration) {
        var fields = registration.getFields();
        var fieldRegistrations = registration.getFieldRegistrations();
        var jsBuilder = new StringBuilder();
        for (var i = 0; i < fields.length; i++) {
            var field = fields[i];
            var fieldRegistration = fieldRegistrations[i];
            jsSerializer(fieldRegistration.serializer()).writeObject(jsBuilder, "packet." + field.getName(), 1, field, fieldRegistration);
        }
        return jsBuilder.toString();
    }

    private static String readObject(ProtocolRegistration registration) {
        var fields = registration.getFields();
        var fieldRegistrations = registration.getFieldRegistrations();
        var jsBuilder = new StringBuilder();
        for (var i = 0; i < fields.length; i++) {
            var field = fields[i];
            var fieldRegistration = fieldRegistrations[i];
            if (field.isAnnotationPresent(Compatible.class)) {
                jsBuilder.append(TAB).append("if (!buffer.isReadable()) {").append(LS);
                jsBuilder.append(TAB + TAB).append("return packet;").append(LS);
                jsBuilder.append(TAB).append("}").append(LS);
            }
            var readObject = jsSerializer(fieldRegistration.serializer()).readObject(jsBuilder, 1, field, fieldRegistration);
            jsBuilder.append(TAB).append(StringUtils.format("packet.{} = {};", field.getName(), readObject)).append(LS);
        }
        return jsBuilder.toString();
    }
}
