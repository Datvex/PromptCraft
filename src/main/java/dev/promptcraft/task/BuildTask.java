package dev.promptcraft.task;

import dev.promptcraft.config.PromptCraftLang;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.session.PromptSessionManager;
import dev.promptcraft.structure.PromptCraftStructure;
import dev.promptcraft.structure.StructureBlockCodec;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

public class BuildTask implements Task {
    // Кладём фиксированное число блоков за тик, независимо от количества операций.
    // Один "fill" на 96x96x96 больше НЕ исполняется за один тик - именно это
    // вешало сервер и роняло клиент в свободном режиме.
    private static final int BLOCKS_PER_TICK = 2048;

    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos origin;
    private final PromptCraftStructure structure;
    private final GenerationSession session;

    private int opIndex = 0;

    // Состояние текущей объёмной операции, растянутой по тикам.
    private boolean opInProgress = false;
    private BlockState opState;
    private int opFlags;
    private boolean opHollow;
    private int opMinX, opMinY, opMinZ, opMaxX, opMaxY, opMaxZ;
    private int curX, curY, curZ;

    private final Set<String> unknownBlocks = new LinkedHashSet<>();

    public BuildTask(ServerPlayerEntity player, BlockPos origin, PromptCraftStructure structure, GenerationSession session) {
        this.player = player;
        this.world = (ServerWorld) player.getWorld();
        this.origin = origin;
        this.structure = structure;
        this.session = session;
        if (session != null) session.markBuildStarted();
    }

    @Override
    public boolean tick() {
        if (session != null && session.isCancelled()) {
            session.markBuildFinished();
            return true;
        }
        if (structure == null || structure.operations == null) {
            return finish();
        }

        int budget = BLOCKS_PER_TICK;

        while (budget > 0) {
            if (!opInProgress) {
                if (opIndex >= structure.operations.size()) {
                    return finish();
                }
                PromptCraftStructure.Operation op = structure.operations.get(opIndex);

                if (op.block == null) { opIndex++; continue; }

                var stateOpt = StructureBlockCodec.parse(op.block);
                if (stateOpt.isEmpty()) {
                    unknownBlocks.add(op.block.split("\\[")[0]);
                    opIndex++;
                    continue;
                }

                BlockState state = stateOpt.get();
                int flags = BlockPlacementUtil.flagsFor(state);

                if ("place".equals(op.type) && op.pos != null && op.pos.length == 3) {
                    world.setBlockState(origin.add(op.pos[0], op.pos[1], op.pos[2]), state, flags);
                    budget--;
                    opIndex++;
                    continue;
                }

                if (("fill".equals(op.type) || "hollow_box".equals(op.type))
                        && op.from != null && op.to != null && op.from.length == 3 && op.to.length == 3) {
                    opState = state;
                    opFlags = flags;
                    opHollow = "hollow_box".equals(op.type);
                    opMinX = Math.min(op.from[0], op.to[0]); opMaxX = Math.max(op.from[0], op.to[0]);
                    opMinY = Math.min(op.from[1], op.to[1]); opMaxY = Math.max(op.from[1], op.to[1]);
                    opMinZ = Math.min(op.from[2], op.to[2]); opMaxZ = Math.max(op.from[2], op.to[2]);
                    curX = opMinX; curY = opMinY; curZ = opMinZ;
                    opInProgress = true;
                } else {
                    opIndex++;
                    continue;
                }
            }

            // Объёмная операция в процессе. Каждая посещённая ячейка тратит бюджет
            // (в т.ч. пропущенная внутренность hollow_box), чтобы даже гигантский
            // fill не мог заблокировать тик.
            while (budget > 0 && curY <= opMaxY) {
                boolean skip = opHollow
                        && curX > opMinX && curX < opMaxX
                        && curY > opMinY && curY < opMaxY
                        && curZ > opMinZ && curZ < opMaxZ;
                if (!skip) {
                    world.setBlockState(origin.add(curX, curY, curZ), opState, opFlags);
                }
                budget--;

                curX++;
                if (curX > opMaxX) {
                    curX = opMinX;
                    curZ++;
                    if (curZ > opMaxZ) {
                        curZ = opMinZ;
                        curY++;
                    }
                }
            }

            if (curY > opMaxY) {
                opInProgress = false;
                opIndex++;
            }
        }

        return false;
    }

    private boolean finish() {
        if (!unknownBlocks.isEmpty()) {
            player.sendMessage(Text.literal(PromptCraftLang.t("Warning: AI referenced unknown block(s), skipped: ", "Внимание: ИИ использовал неизвестные блоки, пропущены: ") + String.join(", ", unknownBlocks))
                    .formatted(Formatting.YELLOW), false);
        }
        player.sendMessage(Text.literal(PromptCraftLang.t("Building complete!", "Постройка завершена!")).formatted(Formatting.GREEN), false);
        if (session != null) session.markBuildFinished();
        PromptSessionManager.clearGeneration(player);
        PromptCraftNetworking.sendAiStreamEvent(player, "done", "");
        return true;
    }
}