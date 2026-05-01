package com.gtnewhorizons.gtnhgradle.modules.ideintegration;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IdeaMiscXmlUpdater {

    /** The default {@code .idea/misc.xml} for IntelliJ */
    private static final @NotNull String MISC_XML_DEFAULT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="EntryPointsManager">
            <list size="4">
              <item index="0" class="java.lang.String" itemvalue="cpw.mods.fml.common.Mod.EventHandler" />
              <item index="1" class="java.lang.String" itemvalue="cpw.mods.fml.common.eventhandler.SubscribeEvent" />
              <item index="2" class="java.lang.String" itemvalue="net.minecraftforge.fml.common.eventhandler.SubscribeEvent" />
              <item index="3" class="java.lang.String" itemvalue="org.spongepowered.asm.mixin.Mixin" />
            </list>
          </component>
          <component name="ExternalStorageConfigurationManager" enabled="true" />
          <component name="ProjectRootManager" version="2">
            <output url="file://$PROJECT_DIR$/build/ideaBuild" />
          </component>
        </project>
        """;

    private static final String TAG_COMPONENT = "component";
    private static final String ATT_NAME = "name";

    public static void mergeOrCreate(Path target) throws Exception {
        final SAXBuilder builder = new SAXBuilder();
        final Document refDoc = builder.build(new StringReader(MISC_XML_DEFAULT));
        final Document targetDoc;

        if (Files.exists(target)) {
            targetDoc = builder.build(Files.newInputStream(target));
        } else {
            targetDoc = refDoc.clone();
            writeDocument(targetDoc, target);
            return;
        }

        Element refRoot = refDoc.getRootElement();
        Element tgtRoot = targetDoc.getRootElement();

        for (Element refComp : refRoot.getChildren(TAG_COMPONENT)) {
            String refCompName = refComp.getAttributeValue(ATT_NAME);
            Element tgtComp = getTargetComponent(tgtRoot, refCompName);

            if (tgtComp == null) {
                tgtRoot.addContent(refComp.clone());
            } else {
                switch (refCompName) {
                    case "EntryPointsManager" -> updateEntryPointsManager(refComp, tgtComp);
                    case "ProjectRootManager" -> updateProjectRootManager(refComp, tgtComp);
                }
            }
        }

        writeDocument(targetDoc, target);
    }

    private static Element getTargetComponent(Element tgtRoot, String refCompName) {
        for (Element c : tgtRoot.getChildren(TAG_COMPONENT)) {
            if (c.getAttributeValue(ATT_NAME)
                .equals(refCompName)) {
                return c;
            }
        }
        return null;
    }

    private static final String TAG_OUTPUT = "output";
    private static final String ATT_URL = "url";

    private static void updateProjectRootManager(Element refComp, Element tgtComp) {
        final Element refOutput = refComp.getChild(TAG_OUTPUT);
        if (refOutput == null) return;
        final Attribute refUrl = refOutput.getAttribute(ATT_URL);
        if (refUrl == null) return;

        final Element tgtOutput = tgtComp.getChild(TAG_OUTPUT);
        if (tgtOutput == null) {
            tgtComp.addContent(refOutput.clone());
            return;
        }

        // Only modify the output url if it doesn't yet have one,
        // or if the existing one is blank somehow.
        // This is a sensible default for most setups
        final Attribute tgtUrl = tgtOutput.getAttribute(ATT_URL);
        if (tgtUrl == null || tgtUrl.getValue()
            .isEmpty()) {
            tgtOutput.setAttribute(refUrl);
        }
    }

    private static final String TAG_LIST = "list";
    private static final String TAG_ITEM = "item";
    private static final String ATT_ITEMVALUE = "itemvalue";

    private static void updateEntryPointsManager(Element refComp, Element tgeComp) {

        final Element modelList = refComp.getChild(TAG_LIST);
        if (modelList == null) return;

        final Element targetList = tgeComp.getChild(TAG_LIST);
        if (targetList == null) {
            tgeComp.addContent(modelList.clone());
            return;
        }

        for (Element modelItem : modelList.getChildren(TAG_ITEM)) {
            addItemIfAbsent(targetList, modelItem);
        }

        final List<Element> targetItems = targetList.getChildren(TAG_ITEM);
        final int targetItemsSize = targetItems.size();
        for (int i = 0; i < targetItemsSize; i++) {
            targetItems.get(i)
                .setAttribute("index", java.lang.String.valueOf(i));
        }
        targetList.setAttribute("size", java.lang.String.valueOf(targetItemsSize));
    }

    private static void addItemIfAbsent(Element targetList, Element modelItem) {

        for (Element item : targetList.getChildren(TAG_ITEM)) {
            if (modelItem.getAttributeValue(ATT_ITEMVALUE)
                .equals(item.getAttributeValue(ATT_ITEMVALUE))) {
                return;
            }
        }
        targetList.addContent(modelItem.clone());
    }

    private static final XMLOutputter PRETTY_XML_OUTPUTTER = new XMLOutputter(
        Format.getPrettyFormat()
            .setIndent("  ")
            .setLineSeparator("\n"));

    private static void writeDocument(Document doc, Path target) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            PRETTY_XML_OUTPUTTER.output(doc, writer);
        }
    }
}
