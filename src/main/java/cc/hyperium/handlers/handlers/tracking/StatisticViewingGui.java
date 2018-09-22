package cc.hyperium.handlers.handlers.tracking;

import cc.hyperium.Hyperium;
import cc.hyperium.gui.HyperiumGui;
import cc.hyperium.utils.RenderUtils;
import club.sk1er.website.api.requests.HypixelApiPlayer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StatisticViewingGui extends HyperiumGui {

    private static ValueTrackingType currentType = ValueTrackingType.COINS;
    private final int DATA_POINTS = 100;
    int timeFac = 0;
    private long masterTimeOne = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
    private long masterTimeTwo = System.currentTimeMillis();
    private List<ValueTrackingItem> masterDataSet;
    private List<ValueTrackingType> types = new ArrayList<>(Arrays.asList(ValueTrackingType.values())); //so we get full arraylist operations

    public StatisticViewingGui() {
        types.remove(ValueTrackingType.ERROR);
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshData();
    }

    private void refreshData() {
        //Guaranteed to be in ascending order

        masterDataSet = Hyperium.INSTANCE.getHandlers().getHypixelValueTracking().getItemsBetween(masterTimeOne, masterTimeTwo);

        ArrayList<ValueTrackingItem> tmp = new ArrayList<>(masterDataSet);
        masterDataSet.clear();
        long delta = (masterTimeTwo - masterTimeOne) / DATA_POINTS;
        HashMap<Integer, List<ValueTrackingItem>> itemMap = new HashMap<>();
        for (ValueTrackingItem valueTrackingItem : tmp) {
            itemMap.computeIfAbsent((int) ((valueTrackingItem.getTime() - masterTimeOne) / delta), integer -> new ArrayList<>()).add(valueTrackingItem);
        }


        for (int integer = 0; integer < DATA_POINTS; integer++) {
            List<ValueTrackingItem> valueTrackingItems = itemMap.get(integer);
            if (valueTrackingItems == null) {
                for (ValueTrackingType type : types) {
                    MissingDataHandling missingDataHandling = type.getMissingDataHandling();
                    if (missingDataHandling == MissingDataHandling.ZERO) {
                        masterDataSet.add(new ValueTrackingItem(type, 0, masterTimeOne + delta * (long) integer));
                    } else {
                        //Find average of values on both sides.
                        ValueTrackingItem left = null;
                        int lI = 0;
                        ValueTrackingItem right = null;
                        int rI = 0;
                        for (int j = integer; j >= 0; j--) {
                            List<ValueTrackingItem> tmp1 = itemMap.get(integer);
                            if (tmp1 != null) {
                                for (ValueTrackingItem valueTrackingItem : tmp1) {
                                    if (valueTrackingItem.getType() == type) {
                                        left = valueTrackingItem;
                                        lI = j;
                                    }
                                }
                            }
                        }
                        for (int j = integer; j < DATA_POINTS; j++) {
                            List<ValueTrackingItem> tmp1 = itemMap.get(integer);
                            if (tmp1 != null) {
                                for (ValueTrackingItem valueTrackingItem : tmp1) {
                                    if (valueTrackingItem.getType() == type) {
                                        right = valueTrackingItem;
                                        rI = j;
                                    }
                                }
                            }
                        }
                        if (left == null || right == null) {
                            masterDataSet.add(new ValueTrackingItem(type, 0, masterTimeOne + delta * (long) integer));
                            continue;
                        }
                        int delta1 = right.getValue() - left.getValue();
                        rI -= lI;
                        double percent = (double) integer / (double) rI;

                        double v = left.getValue() + (percent * ((double) delta1));
                        masterDataSet.add(new ValueTrackingItem(type, (int) v, masterTimeOne + delta * (long) integer));

                    }

                }
                continue;
            }
            HashMap<ValueTrackingType, List<ValueTrackingItem>> map = new HashMap<>();
            for (ValueTrackingItem valueTrackingItem : valueTrackingItems) {
                map.computeIfAbsent(valueTrackingItem.getType(), valueTrackingType -> new ArrayList<>()).add(valueTrackingItem);
            }
            for (ValueTrackingType type : map.keySet()) {
                if (type.getCompressionType() == CompressionType.SUM) {
                    int sum = 0;
                    for (ValueTrackingItem valueTrackingItem : valueTrackingItems) {
                        sum += valueTrackingItem.getValue();
                    }
                    masterDataSet.add(new ValueTrackingItem(type, sum, masterTimeOne + delta * (long) integer));
                } else if (type.getCompressionType() == CompressionType.MAX) {
                    int max = -Integer.MAX_VALUE;
                    for (ValueTrackingItem valueTrackingItem : valueTrackingItems) {
                        max = Math.max(max, valueTrackingItem.getValue());
                    }
                    masterDataSet.add(new ValueTrackingItem(type, max, masterTimeOne + delta * (long) integer));
                }
            }
        }
    }

    @Override
    protected void pack() {
        reg("Next Graph", new GuiButton(nextId(), 1, 1, "Next Graph"), button -> {
            int i = types.indexOf(currentType);
            i++;
            if (i > types.size() - 1)
                i = 0;
            currentType = types.get(i);
        }, button -> {

        });
        reg("Time", new GuiButton(nextId(), 1, 22, "Change Time"), button -> {
            timeFac++;
            if (timeFac > 3)
                timeFac = 0;
            masterTimeTwo = System.currentTimeMillis();
            if (timeFac == 0) {
                masterTimeOne = masterTimeTwo - TimeUnit.HOURS.toMillis(1);

            } else if (timeFac == 1) {
                masterTimeOne = masterTimeTwo - TimeUnit.DAYS.toMillis(1);

            } else if (timeFac == 2) {
                masterTimeOne = masterTimeTwo - TimeUnit.DAYS.toMillis(7);

            } else if (timeFac == 3) {
                masterTimeOne = masterTimeTwo - TimeUnit.DAYS.toMillis(30);
            }
            refreshData();

        }, button -> {
            String tmp = "Set range to: ";
            if (timeFac == 0) {
                tmp += "1 day";
            } else if (timeFac == 1) {
                tmp += "1 week";

            } else if (timeFac == 2) {
                tmp += "1 month";

            } else if (timeFac == 3) {
                tmp += "1 hour";
            }
            button.displayString = tmp;
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawScaledText(currentType.getDisplay(), width / 2, 10, 2.0F, Color.WHITE.getRGB(), true, true);
        DateFormat df = HypixelApiPlayer.DMYHHMMSS;
        drawScaledText(df.format(new Date(masterTimeOne)) + " - " + df.format(new Date(masterTimeTwo)), width / 2, 30, 1.5, Color.WHITE.getRGB(), true, true);
        int xg = width / 10;
        int yg = height / 7;
        ArrayList<ValueTrackingItem> currentDataSet = new ArrayList<>(masterDataSet);
        currentDataSet.removeIf(item -> item.getType() != currentType);
        int max = 0;
        for (ValueTrackingItem valueTrackingItem : currentDataSet) {
            max = Math.max(max, valueTrackingItem.getValue());
        }
        if (max == 0)
            max = 100;
        drawScaledText(Integer.toString(max), xg - fontRendererObj.getStringWidth(Integer.toString(max)), yg, 1.0, Color.WHITE.getRGB(), true, true);
        drawScaledText("0", xg - fontRendererObj.getStringWidth("0"), yg*6-10, 1.0, Color.WHITE.getRGB(), true, true);

        GlStateManager.pushMatrix();
        RenderUtils.drawRect(xg, yg, xg * 9, yg * 6, new Color(0, 0, 0, 100).getRGB());
        GlStateManager.translate(xg, yg, 0);
        int chartWidth = xg * 8;
        long delta = masterTimeTwo - masterTimeOne;

        int size = currentDataSet.size();
        for (int i = 0; i < size; i++) {
            ValueTrackingItem valueTrackingItem = currentDataSet.get(i);
            GlStateManager.pushMatrix();
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(2848);
            GL11.glHint(GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_NICEST);
            GL11.glBegin(6);
            GlStateManager.resetColor();
            GlStateManager.color(1.0F, 0, 0);

            long time = valueTrackingItem.getTime();
            time -= masterTimeOne;
            double v = (double) time / (double) delta;
            int xPos = (int) (v * (double) chartWidth);
            int yPos = (int) ((double) valueTrackingItem.getValue() / (double) max * (double) yg * 5D);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glLineWidth(6);
            int x2 = 0;
            int y2 = 0;
            if (i + 1 < size - 1) {
                ValueTrackingItem valueTrackingItem1 = currentDataSet.get(i + 1);
                long time1 = valueTrackingItem1.getTime();
                time1 -= masterTimeOne;
                x2 = (int) ((double) time1 / (double) delta * (double) chartWidth);
                y2 = (int) ((double) valueTrackingItem1.getValue() / (double) max * (double) yg * 5D);
            }
            GL11.glVertex2d(xPos, yg * 5 - yPos);
            GL11.glVertex2d(xPos, yg * 5);
            GL11.glVertex2d(x2, yg * 5);
            GL11.glVertex2d(x2, yg * 5 - y2);

            GL11.glEnd();
            GL11.glEnable(3553);
            GL11.glDisable(3042);
            GL11.glDisable(2848);
            GlStateManager.popMatrix();

        }
        GlStateManager.popMatrix();
    }

    enum CompressionType {
        MAX,
        SUM,
    }

    enum MissingDataHandling {
        ZERO,
        AVERAGE
    }


}
