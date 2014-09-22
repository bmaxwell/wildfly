/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO;
import static org.jboss.as.logging.AbstractHandlerDefinition.FORMATTER;
import static org.jboss.as.logging.AbstractHandlerDefinition.NAMED_FORMATTER;
import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.ASYNC_HANDLER;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.LoggerResourceDefinition.CATEGORY;
import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.ConsoleHandlerResourceDefinition.CONSOLE_HANDLER;
import static org.jboss.as.logging.CustomHandlerResourceDefinition.CUSTOM_HANDLER;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.FileHandlerResourceDefinition.FILE_HANDLER;
import static org.jboss.as.logging.CommonAttributes.FILTER_SPEC;
import static org.jboss.as.logging.CommonAttributes.HANDLERS;
import static org.jboss.as.logging.CommonAttributes.HANDLER_NAME;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.LoggerResourceDefinition.LOGGER;
import static org.jboss.as.logging.CommonAttributes.LOGGING_PROFILE;
import static org.jboss.as.logging.CommonAttributes.LOGGING_PROFILES;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX;
import static org.jboss.as.logging.CommonAttributes.MAX_INCLUSIVE;
import static org.jboss.as.logging.CommonAttributes.MAX_LEVEL;
import static org.jboss.as.logging.CommonAttributes.MIN_INCLUSIVE;
import static org.jboss.as.logging.CommonAttributes.MIN_LEVEL;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.NAME;
import static org.jboss.as.logging.CustomFormatterResourceDefinition.CUSTOM_FORMATTER;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.OVERFLOW_ACTION;
import static org.jboss.as.logging.CommonAttributes.FILTER_PATTERN;
import static org.jboss.as.logging.PeriodicHandlerResourceDefinition.PERIODIC_ROTATING_FILE_HANDLER;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.QUEUE_LENGTH;
import static org.jboss.as.logging.CommonAttributes.REPLACEMENT;
import static org.jboss.as.logging.CommonAttributes.REPLACE_ALL;
import static org.jboss.as.logging.RootLoggerResourceDefinition.ROOT_LOGGER_PATH_NAME;
import static org.jboss.as.logging.RootLoggerResourceDefinition.ROOT_LOGGER_ATTRIBUTE_NAME;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.ROTATE_SIZE;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_FILE_HANDLER;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.SUBHANDLERS;
import static org.jboss.as.logging.PatternFormatterResourceDefinition.PATTERN_FORMATTER;
import static org.jboss.as.logging.PeriodicHandlerResourceDefinition.SUFFIX;
import static org.jboss.as.logging.PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_FILE_HANDLER;
import static org.jboss.as.logging.ConsoleHandlerResourceDefinition.TARGET;
import static org.jboss.as.logging.LoggerResourceDefinition.USE_PARENT_HANDLERS;
import static org.jboss.as.logging.LoggingMessages.MESSAGES;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.APP_NAME;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.FACILITY;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.HOSTNAME;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.PORT;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.SERVER_ADDRESS;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.SYSLOG_FORMATTER;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.SYSLOG_HANDLER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingSubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    static final LoggingSubsystemParser INSTANCE = new LoggingSubsystemParser();

    private LoggingSubsystemParser() {
        //
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> list) throws XMLStreamException {
        // No attributes
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }
        final PathAddress address = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, LoggingExtension.SUBSYSTEM_NAME));

        // Subsystem add operation
        final ModelNode subsystemAddOp = Util.createAddOperation(address);
        list.add(subsystemAddOp);

        final List<ModelNode> loggerOperations = new ArrayList<ModelNode>();
        final List<ModelNode> asyncHandlerOperations = new ArrayList<ModelNode>();
        final List<ModelNode> handlerOperations = new ArrayList<ModelNode>();
        final List<ModelNode> formatterOperations = new ArrayList<ModelNode>();

        // Elements
        final Set<String> loggerNames = new HashSet<String>();
        final Set<String> handlerNames = new HashSet<String>();
        final Set<String> formatterNames = new HashSet<String>();
        boolean gotRoot = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
            switch (namespace) {
                case LOGGING_1_0:
                case LOGGING_1_1:
                case LOGGING_1_2:
                case LOGGING_1_3:
                case LOGGING_1_4:
                case LOGGING_1_5: {
                    final Element element = Element.forName(reader.getLocalName());
                    switch (element) {
                        case ADD_LOGGING_API_DEPENDENCIES:{
                            if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1 ||
                                    namespace == Namespace.LOGGING_1_2 || namespace == Namespace.LOGGING_1_3)
                                throw unexpectedElement(reader);
                            final String value = ParseUtils.readStringAttributeElement(reader, Attribute.VALUE.getLocalName());
                            LoggingRootResource.ADD_LOGGING_API_DEPENDENCIES.parseAndSetParameter(value, subsystemAddOp, reader);
                            break;
                        }
                        case LOGGER: {
                            parseLoggerElement(reader, address, loggerOperations, loggerNames);
                            break;
                        }
                        case ROOT_LOGGER: {
                            if (gotRoot) {
                                throw unexpectedElement(reader);
                            }
                            gotRoot = true;
                            parseRootLoggerElement(reader, address, loggerOperations);
                            break;
                        }
                        case CONSOLE_HANDLER: {
                            parseConsoleHandlerElement(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case FILE_HANDLER: {
                            parseFileHandlerElement(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case CUSTOM_HANDLER: {
                            if (namespace == Namespace.LOGGING_1_0)
                                throw unexpectedElement(reader);
                            parseCustomHandlerElement(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case PERIODIC_ROTATING_FILE_HANDLER: {
                            parsePeriodicRotatingFileHandlerElement(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case PERIODIC_SIZE_ROTATING_FILE_HANDLER: {
                            if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1 ||
                                    namespace == Namespace.LOGGING_1_2 || namespace == Namespace.LOGGING_1_3 ||
                                    namespace == Namespace.LOGGING_1_4)
                                throw unexpectedElement(reader);
                            parsePeriodicSizeRotatingHandlerElement(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case SIZE_ROTATING_FILE_HANDLER: {
                            parseSizeRotatingHandlerElement(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case ASYNC_HANDLER: {
                            parseAsyncHandlerElement(reader, address, asyncHandlerOperations, handlerNames);
                            break;
                        }
                        case SYSLOG_HANDLER: {
                            parseSyslogHandler(reader, address, handlerOperations, handlerNames);
                            break;
                        }
                        case LOGGING_PROFILES:
                            if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1)
                                throw unexpectedElement(reader);
                            parseLoggingProfilesElement(reader, address, list);
                            break;
                        case FORMATTER:
                            if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1 ||
                                    namespace == Namespace.LOGGING_1_2 || namespace == Namespace.LOGGING_1_3) {
                                throw unexpectedElement(reader);
                            }
                            parseFormatter(reader, address, formatterOperations, formatterNames);
                            break;
                        default: {
                            reader.handleAny(list);
                            break;
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        list.addAll(formatterOperations);
        list.addAll(handlerOperations);
        list.addAll(asyncHandlerOperations);
        list.addAll(loggerOperations);
    }


    static void parseLoggerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode op = new ModelNode();
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.CATEGORY);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case CATEGORY: {
                    name = value;
                    break;
                }
                case USE_PARENT_HANDLERS: {
                    USE_PARENT_HANDLERS.parseAndSetParameter(value, op, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        assert name != null;
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(address.toModelNode()).add(LOGGER, name);

        // Element
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw duplicateNamedElement(reader, reader.getLocalName());
            }
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), op, reader);
                    break;
                }
                case HANDLERS: {
                    parseHandlersElement(op.get(HANDLERS.getName()), reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, op, reader);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
        list.add(op);
    }

    static void parseAsyncHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode node = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case ENABLED:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ENABLED.parseAndSetParameter(value, node, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(ASYNC_HANDLER, name);

        // Elements
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case SUBHANDLERS: {
                    parseHandlersElement(node.get(SUBHANDLERS.getName()), reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, node);
                    break;
                }
                case QUEUE_LENGTH: {
                    QUEUE_LENGTH.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case OVERFLOW_ACTION: {
                    OVERFLOW_ACTION.parseAndSetParameter(readStringAttributeElement(reader, "value").toUpperCase(Locale.US), node, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        list.add(node);
    }

    static void parseRootLoggerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list) throws XMLStreamException {
        // No attributes
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }

        final ModelNode node = new ModelNode();
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(ROOT_LOGGER_PATH_NAME, ROOT_LOGGER_ATTRIBUTE_NAME);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (encountered.contains(element)) {
                throw duplicateNamedElement(reader, reader.getLocalName());
            }
            encountered.add(element);
            switch (element) {
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case HANDLERS: {
                    parseHandlersElement(node.get(HANDLERS.getName()), reader);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
        list.add(node);
    }

    static void parseConsoleHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode node = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, node, reader);
                    break;
                }
                case ENABLED:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ENABLED.parseAndSetParameter(value, node, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Set-up the operation
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(CONSOLE_HANDLER, name);

        // Elements
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, node);
                    break;
                }
                case TARGET: {
                    final String target = readStringAttributeElement(reader, "name");
                    TARGET.parseAndSetParameter(target, node, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        list.add(node);
    }

    static void parseFileHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode node = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, node, reader);
                    break;
                }
                case ENABLED:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ENABLED.parseAndSetParameter(value, node, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(FILE_HANDLER, name);

        // Elements
        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, node);
                    break;
                }
                case FILE: {
                    parseFileElement(node.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!requiredElem.isEmpty()) {
            throw missingRequired(reader, requiredElem);
        }
        list.add(node);
    }

    static void parseCustomHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode node = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.CLASS, Attribute.MODULE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case CLASS: {
                    CLASS.parseAndSetParameter(value, node, reader);
                    break;
                }
                case MODULE: {
                    MODULE.parseAndSetParameter(value, node, reader);
                    break;
                }
                case ENABLED:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ENABLED.parseAndSetParameter(value, node, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }
        // Setup the operation
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(CUSTOM_HANDLER, name);


        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, node);
                    break;
                }
                case PROPERTIES: {
                    parsePropertyElement(node, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        list.add(node);
    }

    static void parsePeriodicRotatingFileHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode node = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, node, reader);
                    break;
                }
                case ENABLED:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ENABLED.parseAndSetParameter(value, node, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(PERIODIC_ROTATING_FILE_HANDLER, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE, Element.SUFFIX);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, node);
                    break;
                }
                case FILE: {
                    parseFileElement(node.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case SUFFIX: {
                    SUFFIX.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!requiredElem.isEmpty()) {
            throw missingRequired(reader, requiredElem);
        }
        list.add(node);
    }
    private static void parsePeriodicSizeRotatingHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case ENABLED:
                    ENABLED.parseAndSetParameter(value, operation, reader);
                    break;
                case ROTATE_ON_BOOT:
                    ROTATE_ON_BOOT.parseAndSetParameter(value, operation, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation address
        addOperationAddress(operation, address, PERIODIC_SIZE_ROTATING_FILE_HANDLER, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FILTER_SPEC: {
                    parseFilter(namespace, operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case FILE: {
                    parseFileElement(operation.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case ROTATE_SIZE: {
                    ROTATE_SIZE.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case MAX_BACKUP_INDEX: {
                    MAX_BACKUP_INDEX.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case SUFFIX: {
                    SUFFIX.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }

    static void parseSizeRotatingHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode node = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, node, reader);
                    break;
                }
                case ENABLED:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ENABLED.parseAndSetParameter(value, node, reader);
                    break;
                case ROTATE_ON_BOOT:
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1 || namespace == Namespace.LOGGING_1_2) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ROTATE_ON_BOOT.parseAndSetParameter(value, node, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation
        node.get(OP).set(ADD);
        node.get(OP_ADDR).set(address.toModelNode()).add(SIZE_ROTATING_FILE_HANDLER, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), node, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case FILTER_SPEC:
                case FILTER: {
                    parseFilter(namespace, node, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, node);
                    break;
                }
                case FILE: {
                    parseFileElement(node.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case ROTATE_SIZE: {
                    ROTATE_SIZE.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case MAX_BACKUP_INDEX: {
                    MAX_BACKUP_INDEX.parseAndSetParameter(readStringAttributeElement(reader, "value"), node, reader);
                    break;
                }
                case SUFFIX: {
                    if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1 ||
                            namespace == Namespace.LOGGING_1_2 || namespace == Namespace.LOGGING_1_3 ||
                            namespace == Namespace.LOGGING_1_4) {
                        throw unexpectedElement(reader);
                    }
                    SizeRotatingHandlerResourceDefinition.SUFFIX.parseAndSetParameter(readValueAttribute(reader), node, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        list.add(node);
    }

    private static void parseSyslogHandler(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> names) throws XMLStreamException {
        final ModelNode model = new ModelNode();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case ENABLED:
                    ENABLED.parseAndSetParameter(value, model, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation
        model.get(OP).set(ADD);
        model.get(OP_ADDR).set(address.toModelNode()).add(SYSLOG_HANDLER, name);

        final EnumSet<Element> requiredElem = EnumSet.noneOf(Element.class);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
            switch (element) {
                case APP_NAME: {
                    APP_NAME.parseAndSetParameter(readStringAttributeElement(reader, "value"), model, reader);
                    break;
                }
                case FACILITY: {
                    FACILITY.parseAndSetParameter(readStringAttributeElement(reader, "value"), model, reader);
                    break;
                }
                case HOSTNAME: {
                    HOSTNAME.parseAndSetParameter(readStringAttributeElement(reader, "value"), model, reader);
                    break;
                }
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readStringAttributeElement(reader, "name"), model, reader);
                    break;
                }
                case FORMATTER: {
                    if (reader.nextTag() != START_ELEMENT) {
                        throw new XMLStreamException(MESSAGES.missingRequiredNestedFilterElement(), reader.getLocation());
                    }
                    switch (Element.forName(reader.getLocalName())) {
                        case SYSLOG_FORMATTER: {
                            requireSingleAttribute(reader, Attribute.SYSLOG_TYPE.getLocalName());
                            model.get(SYSLOG_FORMATTER.getName()).set(readStringAttributeElement(reader, Attribute.SYSLOG_TYPE.getLocalName()));
                            requireNoContent(reader);
                            break;
                        }
                        default: {
                            throw unexpectedElement(reader);
                        }
                    }
                    break;
                }
                case PORT: {
                    PORT.parseAndSetParameter(readStringAttributeElement(reader, "value"), model, reader);
                    break;
                }
                case SERVER_ADDRESS: {
                    SERVER_ADDRESS.parseAndSetParameter(readStringAttributeElement(reader, "value"), model, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        list.add(model);
    }

    private static void parseFileElement(final ModelNode node, final XMLExtendedStreamReader reader) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATH: {
                    PATH.parseAndSetParameter(value, node, reader);
                    break;
                }
                case RELATIVE_TO: {
                    RELATIVE_TO.parseAndSetParameter(value, node, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }
        requireNoContent(reader);
    }

    private static void parseFormatter(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case PATTERN_FORMATTER: {
                    final ModelNode operation = Util.createAddOperation();
                    // Setup the operation address
                    addOperationAddress(operation, address, PatternFormatterResourceDefinition.PATTERN_FORMATTER.getName(), name);
                    parsePatternFormatterElement(reader, operation);
                    operations.add(operation);
                    break;
                }
                case CUSTOM_FORMATTER: {
                    final ModelNode operation = Util.createAddOperation();
                    // Setup the operation address
                    addOperationAddress(operation, address, CustomFormatterResourceDefinition.CUSTOM_FORMATTER.getName(), name);
                    parseCustomFormatterElement(reader, operation);
                    operations.add(operation);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private static void parseHandlerFormatterElement(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }
        boolean formatterDefined = false;
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PATTERN_FORMATTER: {
                    if (formatterDefined) {
                        throw unexpectedElement(reader);
                    }
                    requireSingleAttribute(reader, PatternFormatterResourceDefinition.PATTERN.getName());
                    formatterDefined = true;
                    FORMATTER.parseAndSetParameter(readStringAttributeElement(reader, PatternFormatterResourceDefinition.PATTERN.getName()), operation, reader);
                    break;
                }
                case NAMED_FORMATTER: {
                    if (formatterDefined) {
                        throw unexpectedElement(reader);
                    }
                    formatterDefined = true;
                    NAMED_FORMATTER.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private static void parsePatternFormatterElement(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.PATTERN);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATTERN: {
                    PatternFormatterResourceDefinition.PATTERN.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case COLOR_MAP: {
                    PatternFormatterResourceDefinition.COLOR_MAP.parseAndSetParameter(value, operation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        requireNoContent(reader);
    }

    private static void parseCustomFormatterElement(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.CLASS, Attribute.MODULE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case CLASS: {
                    CommonAttributes.CLASS.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case MODULE: {
                    CommonAttributes.MODULE.parseAndSetParameter(value, operation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }


        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case PROPERTIES: {
                    parsePropertyElement(operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private static void parsePropertyElement(final ModelNode node, final XMLExtendedStreamReader reader) throws XMLStreamException {
        while (reader.nextTag() != END_ELEMENT) {
            final int cnt = reader.getAttributeCount();
            String name = null;
            String value = null;
            for (int i = 0; i < cnt; i++) {
                requireNoNamespaceAttribute(reader, i);
                final String attrValue = reader.getAttributeValue(i);
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME: {
                        name = attrValue;
                        break;
                    }
                    case VALUE: {
                        value = attrValue;
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
            if (name == null) {
                throw missingRequired(reader, Collections.singleton(Attribute.NAME.getLocalName()));
            }
            node.get(PROPERTIES.getName()).add(name, new ModelNode(value));
            if (reader.nextTag() != END_ELEMENT) {
                throw unexpectedElement(reader);
            }
        }
    }

    private static void parseHandlersElement(final ModelNode node, final XMLExtendedStreamReader reader) throws XMLStreamException {
        // No attributes
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }

        // Elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case HANDLER: {
                    node.add(readStringAttributeElement(reader, "name"));
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static void parseLoggingProfilesElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list) throws XMLStreamException {
        final Set<String> profileNames = new HashSet<String>();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LOGGING_PROFILE: {
                    parseLoggingProfileElement(reader, address, list, profileNames);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    static void parseLoggingProfileElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> list, final Set<String> profileNames) throws XMLStreamException {
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!profileNames.add(name)) {
            throw duplicateNamedElement(reader, name);
        }
        // Setup the address
        final PathAddress profileAddress = address.append(LOGGING_PROFILE, name);
        list.add(Util.createAddOperation(profileAddress));

        final List<ModelNode> loggerOperations = new ArrayList<ModelNode>();
        final List<ModelNode> asyncHandlerOperations = new ArrayList<ModelNode>();
        final List<ModelNode> handlerOperations = new ArrayList<ModelNode>();
        final List<ModelNode> formatterOperations = new ArrayList<ModelNode>();

        final Set<String> loggerNames = new HashSet<String>();
        final Set<String> handlerNames = new HashSet<String>();
        final Set<String> formatterNames = new HashSet<String>();
        final Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        boolean gotRoot = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LOGGER: {
                    parseLoggerElement(reader, profileAddress, loggerOperations, loggerNames);
                    break;
                }
                case ROOT_LOGGER: {
                    if (gotRoot) {
                        throw unexpectedElement(reader);
                    }
                    gotRoot = true;
                    parseRootLoggerElement(reader, profileAddress, loggerOperations);
                    break;
                }
                case CONSOLE_HANDLER: {
                    parseConsoleHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case FILE_HANDLER: {
                    parseFileHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case CUSTOM_HANDLER: {
                    parseCustomHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case PERIODIC_ROTATING_FILE_HANDLER: {
                    parsePeriodicRotatingFileHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case SIZE_ROTATING_FILE_HANDLER: {
                    parseSizeRotatingHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case ASYNC_HANDLER: {
                    parseAsyncHandlerElement(reader, profileAddress, asyncHandlerOperations, handlerNames);
                    break;
                }
                case SYSLOG_HANDLER: {
                    parseSyslogHandler(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case FORMATTER:
                    if (namespace == Namespace.LOGGING_1_2 || namespace == Namespace.LOGGING_1_3)
                        throw unexpectedElement(reader);
                    parseFormatter(reader, profileAddress, formatterOperations, formatterNames);
                    break;
                default: {
                    reader.handleAny(list);
                    break;
                }
            }
        }
        list.addAll(formatterOperations);
        list.addAll(handlerOperations);
        list.addAll(asyncHandlerOperations);
        list.addAll(loggerOperations);
    }

    private static void parseFilter(final Namespace namespace, final ModelNode node, final XMLExtendedStreamReader reader) throws XMLStreamException {
        if (namespace == Namespace.LOGGING_1_0 || namespace == Namespace.LOGGING_1_1) {
            // No attributes
            if (reader.getAttributeCount() > 0) {
                throw unexpectedAttribute(reader, 0);
            }
            final StringBuilder filter = new StringBuilder();
            parseFilterChildren(filter, false, reader);
            node.get(FILTER_SPEC.getName()).set(filter.toString());
        } else {
            FILTER_SPEC.parseAndSetParameter(readStringAttributeElement(reader, Attribute.VALUE.getLocalName()), node, reader);
        }
    }

    private static void parseFilterChildren(final StringBuilder filter, final boolean useDelimiter, final XMLExtendedStreamReader reader) throws XMLStreamException {
        // No attributes
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }
        final char delimiter = ',';

        // Elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ACCEPT: {
                    filter.append(Filters.ACCEPT);
                    requireNoContent(reader);
                    break;
                }
                case ALL: {
                    filter.append(Filters.ALL).append('(');
                    parseFilterChildren(filter, true, reader);
                    // If the last character is a delimiter remove it
                    final int index = filter.length() - 1;
                    if (filter.charAt(index) == delimiter) {
                        filter.setCharAt(index, ')');
                    } else {
                        filter.append(')');
                    }
                    break;
                }
                case ANY: {
                    filter.append(Filters.ANY).append('(');
                    parseFilterChildren(filter, true, reader);
                    // If the last character is a delimiter remove it
                    final int index = filter.length() - 1;
                    if (filter.charAt(index) == delimiter) {
                        filter.setCharAt(index, ')');
                    } else {
                        filter.append(')');
                    }
                    break;
                }
                case CHANGE_LEVEL: {
                    filter.append(Filters.LEVEL_CHANGE)
                            .append('(')
                            .append(readStringAttributeElement(reader, CommonAttributes.NEW_LEVEL.getName()))
                            .append(')');
                    break;
                }
                case DENY: {
                    filter.append(Filters.DENY);
                    requireNoContent(reader);
                    break;
                }
                case LEVEL: {
                    filter.append(Filters.LEVELS)
                            .append('(')
                            .append(readStringAttributeElement(reader, NAME.getName()))
                            .append(')');
                    break;
                }
                case LEVEL_RANGE: {
                    filter.append(Filters.LEVEL_RANGE);
                    final boolean minInclusive = Boolean.parseBoolean(reader.getAttributeValue(null, MIN_INCLUSIVE.getName()));
                    final boolean maxInclusive = Boolean.parseBoolean(reader.getAttributeValue(null, MAX_INCLUSIVE.getName()));
                    if (minInclusive) {
                        filter.append('[');
                    } else {
                        filter.append('(');
                    }
                    filter.append(reader.getAttributeValue(null, MIN_LEVEL.getName())).append(delimiter);
                    filter.append(reader.getAttributeValue(null, MAX_LEVEL.getName()));
                    if (maxInclusive) {
                        filter.append(']');
                    } else {
                        filter.append(')');
                    }
                    requireNoContent(reader);
                    break;
                }
                case MATCH: {
                    filter.append(Filters.MATCH).append("(\"").append(readStringAttributeElement(reader, FILTER_PATTERN.getName())).append("\")");
                    break;
                }
                case NOT: {
                    filter.append(Filters.NOT).append('(');
                    parseFilterChildren(filter, true, reader);
                    // If the last character is a delimiter remove it
                    final int index = filter.length() - 1;
                    if (filter.charAt(index) == delimiter) {
                        filter.setCharAt(index, ')');
                    } else {
                        filter.append(')');
                    }
                    break;
                }
                case REPLACE: {
                    final boolean replaceAll = Boolean.valueOf(reader.getAttributeValue(null, REPLACE_ALL.getName()));
                    if (replaceAll) {
                        filter.append(Filters.SUBSTITUTE_ALL);
                    } else {
                        filter.append(Filters.SUBSTITUTE);
                    }
                    filter.append("(\"")
                            .append(reader.getAttributeValue(null, FILTER_PATTERN.getName()))
                            .append('"')
                            .append(delimiter)
                            .append('"')
                            .append(reader.getAttributeValue(null, REPLACEMENT.getName()))
                            .append("\")");
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
            if (useDelimiter) {
                filter.append(delimiter);
            }

        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(Namespace.CURRENT.getUriString(), false);

        ModelNode node = context.getModelNode();

        // Marshall attributes
        LoggingRootResource.ADD_LOGGING_API_DEPENDENCIES.marshallAsElement(node, false, writer);

        writeContent(writer, node);

        if (node.hasDefined(LOGGING_PROFILE)) {
            final List<Property> profiles = node.get(LOGGING_PROFILE).asPropertyList();
            if (!profiles.isEmpty()) {
                writer.writeStartElement(LOGGING_PROFILES);
                for (Property profile : profiles) {
                    final String name = profile.getName();
                    writer.writeStartElement(LOGGING_PROFILE);
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    writeContent(writer, profile.getValue());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }

    public void writeContent(final XMLExtendedStreamWriter writer, final ModelNode node) throws XMLStreamException {

        if (node.hasDefined(ASYNC_HANDLER)) {
            final ModelNode handlers = node.get(ASYNC_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeAsynchHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(CONSOLE_HANDLER)) {
            final ModelNode handlers = node.get(CONSOLE_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeConsoleHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(FILE_HANDLER)) {
            final ModelNode handlers = node.get(FILE_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeFileHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(CUSTOM_HANDLER)) {
            final ModelNode handlers = node.get(CUSTOM_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeCustomHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(PERIODIC_ROTATING_FILE_HANDLER)) {
            final ModelNode handlers = node.get(PERIODIC_ROTATING_FILE_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writePeriodicRotatingFileHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(PERIODIC_SIZE_ROTATING_FILE_HANDLER)) {
            final ModelNode handlers = node.get(PERIODIC_SIZE_ROTATING_FILE_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writePeriodicSizeRotatingFileHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(SIZE_ROTATING_FILE_HANDLER)) {
            final ModelNode handlers = node.get(SIZE_ROTATING_FILE_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeSizeRotatingFileHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(SYSLOG_HANDLER)) {
            final ModelNode handlers = node.get(SYSLOG_HANDLER);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeSyslogHandler(writer, handler, name);
                }
            }
        }
        if (node.hasDefined(LOGGER)) {
            for (String name : node.get(LOGGER).keys()) {
                writeLogger(writer, name, node.get(LOGGER, name));
            }
        }
        if (node.hasDefined(ROOT_LOGGER_PATH_NAME)) {
            writeRootLogger(writer, node.get(ROOT_LOGGER_PATH_NAME, ROOT_LOGGER_ATTRIBUTE_NAME));
        }

        writeFormatters(writer, PATTERN_FORMATTER, node);
        writeFormatters(writer, CUSTOM_FORMATTER, node);
    }

    private void writeCommonLogger(final XMLExtendedStreamWriter writer, final ModelNode node) throws XMLStreamException {
        LEVEL.marshallAsElement(node, writer);
        FILTER_SPEC.marshallAsElement(node, writer);
        HANDLERS.marshallAsElement(node, writer);
    }

    private void writeCommonHandler(final XMLExtendedStreamWriter writer, final ModelNode node) throws XMLStreamException {
        LEVEL.marshallAsElement(node, writer);
        ENCODING.marshallAsElement(node, writer);
        FILTER_SPEC.marshallAsElement(node, writer);
        FORMATTER.marshallAsElement(node, writer);
        NAMED_FORMATTER.marshallAsElement(node, writer);
    }

    private void writeConsoleHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name)
            throws XMLStreamException {
        writer.writeStartElement(Element.CONSOLE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(node, writer);
        ENABLED.marshallAsAttribute(node, false, writer);
        writeCommonHandler(writer, node);
        TARGET.marshallAsElement(node, writer);
        writer.writeEndElement();
    }

    private void writeFileHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.FILE_HANDLER.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), name);
        AUTOFLUSH.marshallAsAttribute(node, writer);
        ENABLED.marshallAsAttribute(node, false, writer);
        writeCommonHandler(writer, node);
        FILE.marshallAsElement(node, writer);
        APPEND.marshallAsElement(node, writer);

        writer.writeEndElement();
    }

    private void writeCustomHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name)
            throws XMLStreamException {
        writer.writeStartElement(Element.CUSTOM_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        CLASS.marshallAsAttribute(node, writer);
        MODULE.marshallAsAttribute(node, writer);
        ENABLED.marshallAsAttribute(node, false, writer);
        writeCommonHandler(writer, node);
        PROPERTIES.marshallAsElement(node, writer);

        writer.writeEndElement();
    }

    private void writePeriodicRotatingFileHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.PERIODIC_ROTATING_FILE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(node, writer);
        ENABLED.marshallAsAttribute(node, false, writer);
        writeCommonHandler(writer, node);
        FILE.marshallAsElement(node, writer);
        SUFFIX.marshallAsElement(node, writer);
        APPEND.marshallAsElement(node, writer);

        writer.writeEndElement();
    }

    private void writePeriodicSizeRotatingFileHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.PERIODIC_SIZE_ROTATING_FILE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        ROTATE_ON_BOOT.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        FILE.marshallAsElement(model, writer);
        ROTATE_SIZE.marshallAsElement(model, writer);
        MAX_BACKUP_INDEX.marshallAsElement(model, writer);
        SUFFIX.marshallAsElement(model, writer);
        APPEND.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeSizeRotatingFileHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.SIZE_ROTATING_FILE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(node, writer);
        ENABLED.marshallAsAttribute(node, false, writer);
        ROTATE_ON_BOOT.marshallAsAttribute(node, false, writer);
        writeCommonHandler(writer, node);
        FILE.marshallAsElement(node, writer);
        ROTATE_SIZE.marshallAsElement(node, writer);
        MAX_BACKUP_INDEX.marshallAsElement(node, writer);
        APPEND.marshallAsElement(node, writer);
        SizeRotatingHandlerResourceDefinition.SUFFIX.marshallAsElement(node, writer);

        writer.writeEndElement();
    }

    private void writeSyslogHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.SYSLOG_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        ENABLED.marshallAsAttribute(node, false, writer);
        LEVEL.marshallAsElement(node, writer);
        SERVER_ADDRESS.marshallAsElement(node, writer);
        HOSTNAME.marshallAsElement(node, writer);
        PORT.marshallAsElement(node, writer);
        APP_NAME.marshallAsElement(node, writer);
        SYSLOG_FORMATTER.marshallAsElement(node, writer);
        FACILITY.marshallAsElement(node, writer);

        writer.writeEndElement();
    }

    private void writeAsynchHandler(final XMLExtendedStreamWriter writer, final ModelNode node, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.ASYNC_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        ENABLED.marshallAsAttribute(node, false, writer);
        LEVEL.marshallAsElement(node, writer);
        FILTER_SPEC.marshallAsElement(node, writer);
        FORMATTER.marshallAsElement(node, writer);
        QUEUE_LENGTH.marshallAsElement(node, writer);
        OVERFLOW_ACTION.marshallAsElement(node, writer);
        SUBHANDLERS.marshallAsElement(node, writer);

        writer.writeEndElement();
    }

    private void writeLogger(final XMLExtendedStreamWriter writer, String name, final ModelNode node) throws XMLStreamException {
        writer.writeStartElement(Element.LOGGER.getLocalName());
        writer.writeAttribute(CATEGORY.getXmlName(), name);
        USE_PARENT_HANDLERS.marshallAsAttribute(node, writer);
        writeCommonLogger(writer, node);
        writer.writeEndElement();
    }

    private void writeRootLogger(final XMLExtendedStreamWriter writer, final ModelNode node) throws XMLStreamException {
        writer.writeStartElement(Element.ROOT_LOGGER.getLocalName());
        writeCommonLogger(writer, node);
        writer.writeEndElement();
    }

    private void writeFormatters(final XMLExtendedStreamWriter writer, final AttributeDefinition attribute, final ModelNode model) throws XMLStreamException {
        if (model.hasDefined(attribute.getName())) {
            for (String name : model.get(attribute.getName()).keys()) {
                writer.writeStartElement(Element.FORMATTER.getLocalName());
                writer.writeAttribute(NAME.getXmlName(), name);
                final ModelNode value = model.get(attribute.getName(), name);
                attribute.marshallAsElement(value, writer);
                writer.writeEndElement();
            }
        }
    }

    private static void addOperationAddress(final ModelNode operation, final PathAddress base, final String key, final String value) {
        operation.get(OP_ADDR).set(base.append(key, value).toModelNode());
    }

    private static String readNameAttribute(final XMLExtendedStreamReader reader) throws XMLStreamException {
        return readStringAttributeElement(reader, "name");
    }

    private static String readValueAttribute(final XMLExtendedStreamReader reader) throws XMLStreamException {
        return readStringAttributeElement(reader, "value");
    }
}
