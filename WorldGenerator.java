package com.dogapocalypse.worldgen;

import com.dogapocalypse.core.DogApocalypsePlugin;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * WorldGenerator v4 — Geração sem freeze.
 *
 * SOLUÇÃO DO FREEZE:
 *   O Paper trava quando setType() é chamado num chunk não carregado.
 *   Solução: carregamos todos os chunks da área ANTES de colocar blocos,
 *   usando world.loadChunk() de forma síncrona (é rápido quando o chunk
 *   é flat/vazio). Depois colocamos os blocos em lotes pequenos por tick.
 *
 * NÍVEIS:
 *   1 — Cidade em Ruínas    (Z=0    até Z=350)
 *   2 — Floresta Infectada  (Z=1200 até Z=1550)
 *   3 — Ruínas de Guerra    (Z=2400 até Z=2750)
 *   4 — Covil do Dragão     (Z=3600 até Z=3950)
 */
public class WorldGenerator {

    private final DogApocalypsePlugin plugin;
    private World mundo;

    public static final String NOME_MUNDO = "dogapocalypse_niveis";
    private static final int BASE_Y      = 64;
    private static final int ESPACAMENTO = 1200;

    // Blocos colocados por tick — valor conservador para Termux
    private static final int LOTE = 80;

    // Fila global de blocos a colocar: {x, y, z, ordinal}
    private final ArrayList<int[]> fila = new ArrayList<>();

    // Cache de materiais para evitar Material.values() repetidamente
    private static final Material[] MATS = Material.values();

    public WorldGenerator(DogApocalypsePlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────
    // INICIALIZAÇÃO
    // ─────────────────────────────────────────────────────────────

    public void initWorld(Runnable aoTerminar) {
        mundo = Bukkit.getWorld(NOME_MUNDO);
        if (mundo != null) {
            plugin.getLogger().info("[WorldGen] Mundo já existe, pulando geração.");
            if (aoTerminar != null) aoTerminar.run();
            return;
        }

        plugin.getLogger().info("[WorldGen] Criando mundo: " + NOME_MUNDO);
        WorldCreator c = new WorldCreator(NOME_MUNDO);
        c.type(WorldType.FLAT);
        // Mundo flat com apenas bedrock — começamos do zero
        c.generatorSettings("{\"layers\":[{\"block\":\"bedrock\",\"height\":1}],\"biome\":\"plains\"}");
        c.generateStructures(false);

        mundo = c.createWorld();
        if (mundo == null) { plugin.getLogger().severe("[WorldGen] Falha ao criar mundo!"); return; }

        mundo.setSpawnFlags(false, false);
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING,       false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,     false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE,      false);
        mundo.setGameRule(GameRule.MOB_GRIEFING,          false);
        mundo.setGameRule(GameRule.KEEP_INVENTORY,        true);
        mundo.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        mundo.setTime(18000); // noite eterna

        // Pré-carregar chunks de todos os 4 níveis antes de qualquer geração
        plugin.getLogger().info("[WorldGen] Pré-carregando chunks...");
        preCarregarChunks();

        // Iniciar geração nível por nível com delay entre eles
        new BukkitRunnable() {
            int etapa = 0;
            @Override public void run() {
                switch (etapa++) {
                    case 0 -> { plugin.getLogger().info("[WorldGen] Gerando Nível 1: Cidade em Ruínas..."); montarNivel1(); }
                    case 1 -> { plugin.getLogger().info("[WorldGen] Gerando Nível 2: Floresta Infectada..."); montarNivel2(); }
                    case 2 -> { plugin.getLogger().info("[WorldGen] Gerando Nível 3: Ruínas de Guerra..."); montarNivel3(); }
                    case 3 -> { plugin.getLogger().info("[WorldGen] Gerando Nível 4: Covil do Dragão..."); montarNivel4(); }
                    default -> {
                        plugin.getLogger().info("[WorldGen] Todos os níveis prontos!");
                        if (aoTerminar != null) aoTerminar.run();
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 60L);
    }

    /** Carrega todos os chunks das 4 zonas de uma vez. Rápido pois são flat/vazios. */
    private void preCarregarChunks() {
        for (int nivel = 1; nivel <= 4; nivel++) {
            int ozBloco = (nivel - 1) * ESPACAMENTO;
            int czInicio = ozBloco >> 4;
            int czFim    = (ozBloco + 380) >> 4;
            for (int cx = 0; cx <= 4; cx++) {
                for (int cz = czInicio; cz <= czFim; cz++) {
                    mundo.loadChunk(cx, cz, true);
                }
            }
        }
        plugin.getLogger().info("[WorldGen] Chunks pré-carregados.");
    }

    // ═══════════════════════════════════════════════════════════════
    // NÍVEL 1 — CIDADE EM RUÍNAS
    // ═══════════════════════════════════════════════════════════════

    private void montarNivel1() {
        fila.clear();
        int oz = 0;
        Random rng = new Random(101);

        // ── Chão urbano ──
        camada(0, BASE_Y-1, oz,      60, 360, Material.STONE_BRICKS);
        camada(0, BASE_Y-2, oz,      60, 360, Material.COBBLESTONE);
        camada(0, BASE_Y-3, oz,      60, 360, Material.GRAVEL);

        // ── Estradas (asfalto = smooth stone) ──
        camada(24, BASE_Y,  oz,      8,  360, Material.SMOOTH_STONE);   // avenida Z
        camada(0,  BASE_Y,  oz+145,  60, 8,   Material.SMOOTH_STONE);   // cruzamento X
        // Calçadas
        camada(22, BASE_Y,  oz,      2,  360, Material.STONE_BRICK_SLAB);
        camada(32, BASE_Y,  oz,      2,  360, Material.STONE_BRICK_SLAB);

        // ══ BLOCO A — Residencial esquerdo (X 0–21) ══
        predio(1,  BASE_Y, oz+12,   10,8,8,  rng, Material.STONE_BRICKS,        Material.GLASS_PANE);
        predio(13, BASE_Y, oz+12,   8, 6,7,  rng, Material.BRICK,               Material.GLASS_PANE);
        predio(1,  BASE_Y, oz+38,   12,9,9,  rng, Material.STONE_BRICKS,        Material.GLASS_PANE);
        predio(1,  BASE_Y, oz+68,   9, 7,8,  rng, Material.COBBLESTONE,         Material.GLASS_PANE);
        predio(12, BASE_Y, oz+63,   9, 10,8, rng, Material.BRICK,               Material.GLASS_PANE);
        predio(1,  BASE_Y, oz+98,   14,12,9, rng, Material.DEEPSLATE_BRICKS,    Material.TINTED_GLASS);
        predio(1,  BASE_Y, oz+125,  10,8,7,  rng, Material.STONE_BRICKS,        Material.GLASS_PANE);

        // ══ BLOCO B — Comercial direito (X 34–59) ══
        predio(34, BASE_Y, oz+10,   16,14,11,rng, Material.DEEPSLATE_BRICKS,    Material.TINTED_GLASS);
        predio(52, BASE_Y, oz+10,   8, 10,8, rng, Material.CHISELED_STONE_BRICKS,Material.GLASS_PANE);
        predio(34, BASE_Y, oz+47,   20,18,13,rng, Material.POLISHED_DEEPSLATE,  Material.TINTED_GLASS);
        predio(56, BASE_Y, oz+52,   6, 8,6,  rng, Material.BRICK,               Material.GLASS_PANE);
        predio(34, BASE_Y, oz+102,  14,16,10,rng, Material.DEEPSLATE_BRICKS,    Material.TINTED_GLASS);
        predio(50, BASE_Y, oz+102,  10,12,8, rng, Material.STONE_BRICKS,        Material.GLASS_PANE);

        // ══ PRAÇA CENTRAL (Z 145–165) ══
        praca(30, BASE_Y, oz+155);

        // ══ ZONA INDUSTRIAL (Z 180–295) ══
        fabrica(1,  BASE_Y, oz+185, 22,11,18);
        fabrica(34, BASE_Y, oz+178, 20,9, 16);
        fabrica(1,  BASE_Y, oz+240, 18,8, 14);

        // ══ ESTAÇÃO DE TREM DESTRUÍDA (Z 155–175) ══
        estacao(5, BASE_Y, oz+220, 50, 10);

        // ══ CHECKPOINTS ══
        checkpoint(27, BASE_Y, oz+92,  "§6§l✦ PONTO DE CONTROLE 1");
        checkpoint(27, BASE_Y, oz+205, "§6§l✦ PONTO DE CONTROLE 2");

        // ══ ARENA DO BOSS ══
        arenaChefe(30, BASE_Y, oz+310, 24, Material.BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);

        // ── Detalhes de rua ──
        iluminacaoCidade(oz, 320);
        entulho(oz, 310, rng);
        vegetacaoUrbana(oz, 310, rng);

        processarFila();
    }

    // ═══════════════════════════════════════════════════════════════
    // NÍVEL 2 — FLORESTA INFECTADA
    // ═══════════════════════════════════════════════════════════════

    private void montarNivel2() {
        fila.clear();
        int oz = ESPACAMENTO;
        Random rng = new Random(202);

        // ── Chão de floresta ──
        camada(0, BASE_Y-1, oz, 60, 340, Material.MOSS_BLOCK);
        camada(0, BASE_Y-2, oz, 60, 340, Material.MOSSY_COBBLESTONE);
        camada(0, BASE_Y-3, oz, 60, 340, Material.DIRT);

        // Trilha central
        for (int z = oz; z < oz+340; z++)
            for (int x = 26; x <= 33; x++)
                bloco(x, BASE_Y, z, Material.DIRT_PATH);

        // ── Árvores mutantes ──
        for (int z = oz+20; z < oz+300; z += 20) {
            for (int x = 2; x <= 20; x += 9)  arvoreMutante(x, BASE_Y, z, 8+rng.nextInt(8), rng);
            for (int x = 38; x <= 56; x += 9) arvoreMutante(x, BASE_Y, z, 7+rng.nextInt(9), rng);
        }

        // ── Pontes de cipó ──
        ponteSelva(12, BASE_Y+12, oz+55,  oz+95);
        ponteSelva(12, BASE_Y+14, oz+115, oz+155);
        ponteSelva(42, BASE_Y+13, oz+85,  oz+135);
        ponteSelva(12, BASE_Y+15, oz+185, oz+235);

        // ── Zonas de pântano ──
        pantano(0,  BASE_Y, oz+75,  60, 22, rng);
        pantano(0,  BASE_Y, oz+185, 60, 28, rng);

        // ── Ruínas antigas ──
        ruinaAntiga(5,  BASE_Y, oz+105, 14, 6, rng);
        ruinaAntiga(40, BASE_Y, oz+150, 12, 5, rng);
        ruinaAntiga(8,  BASE_Y, oz+238, 16, 7, rng);

        // ── Toca de aranha gigante ──
        tocaAranha(10, BASE_Y, oz+260, rng);

        // ── Checkpoints ──
        checkpoint(27, BASE_Y, oz+105, "§6§l✦ PONTO DE CONTROLE 1");
        checkpoint(27, BASE_Y, oz+215, "§6§l✦ PONTO DE CONTROLE 2");

        // ── Arena do boss — clareira ──
        arenaChefe(30, BASE_Y, oz+290, 26, Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK);

        iluminacaoFloresta(oz, 305);
        processarFila();
    }

    // ═══════════════════════════════════════════════════════════════
    // NÍVEL 3 — RUÍNAS DE GUERRA
    // ═══════════════════════════════════════════════════════════════

    private void montarNivel3() {
        fila.clear();
        int oz = ESPACAMENTO * 2;
        Random rng = new Random(303);

        // ── Chão de concreto ──
        camada(0, BASE_Y-1, oz, 60, 340, Material.SMOOTH_STONE);
        camada(0, BASE_Y-2, oz, 60, 340, Material.IRON_BLOCK);
        camada(0, BASE_Y-3, oz, 60, 340, Material.COBBLESTONE);

        // Estrada com cascalho
        camada(24, BASE_Y, oz, 8, 340, Material.GRAVEL);

        // ── Bunkers e trincheiras ──
        bunker(1,  BASE_Y, oz+18,  16,6,12);
        bunker(42, BASE_Y, oz+18,  18,7,14);
        trincheira(0,  BASE_Y, oz+55, 22, 4);
        trincheira(36, BASE_Y, oz+55, 22, 4);
        bunker(8,  BASE_Y, oz+80,  24,8,16);

        // ── Veículos destruídos ──
        veiculo(5,  BASE_Y, oz+110, rng);
        veiculo(40, BASE_Y, oz+125, rng);
        veiculo(18, BASE_Y, oz+155, rng);

        // ── Crateras ──
        crateras(oz+100, oz+190, rng);

        // ── Complexo industrial ──
        complexoIndustrial(0, BASE_Y, oz+195, oz+268);

        // ── Torre de observação ──
        torreVigia(52, BASE_Y, oz+140, 14);

        // ── Checkpoints ──
        checkpoint(27, BASE_Y, oz+100, "§6§l✦ PONTO DE CONTROLE 1");
        checkpoint(27, BASE_Y, oz+190, "§6§l✦ PONTO DE CONTROLE 2");
        checkpoint(27, BASE_Y, oz+265, "§6§l✦ PONTO DE CONTROLE 3");

        // ── Arena do boss — plataforma metálica ──
        arenaChefe(30, BASE_Y, oz+295, 26, Material.IRON_BLOCK, Material.CHISELED_STONE_BRICKS);

        iluminacaoGuerra(oz, 310);
        processarFila();
    }

    // ═══════════════════════════════════════════════════════════════
    // NÍVEL 4 — COVIL DO DRAGÃO
    // ═══════════════════════════════════════════════════════════════

    private void montarNivel4() {
        fila.clear();
        int oz = ESPACAMENTO * 3;
        Random rng = new Random(404);

        // ── Chão vulcânico ──
        camada(0, BASE_Y-1, oz, 60, 360, Material.BLACKSTONE);
        camada(0, BASE_Y-2, oz, 60, 360, Material.NETHERRACK);
        camada(0, BASE_Y-3, oz, 60, 360, Material.SOUL_SAND);

        // ── Rio de lava central ──
        for (int z = oz+35; z < oz+310; z++)
            for (int x = 26; x <= 33; x++) {
                bloco(x, BASE_Y-1, z, Material.LAVA);
                bloco(x, BASE_Y,   z, Material.AIR);
            }

        // ── Pontes de obsidiana ──
        ponteObsidiana(0,  BASE_Y, oz+80,  26, 8);
        ponteObsidiana(0,  BASE_Y, oz+165, 26, 8);
        ponteObsidiana(0,  BASE_Y, oz+248, 26, 8);

        // ── Pilares vulcânicos ──
        for (int z = oz+25; z < oz+305; z += 22) {
            pilar(2+rng.nextInt(18),  BASE_Y, z, 6+rng.nextInt(8), rng);
            pilar(40+rng.nextInt(18), BASE_Y, z, 5+rng.nextInt(9), rng);
        }

        // ── Túneis de pedra ──
        tunelLava(5,  BASE_Y, oz+50,  oz+95);
        tunelLava(35, BASE_Y, oz+125, oz+160);

        // ── Templo do nether ──
        temploNether(4, BASE_Y, oz+175, 52, 18, 42);

        // ── Altares menores ──
        altar(8,  BASE_Y, oz+60,  rng);
        altar(48, BASE_Y, oz+100, rng);
        altar(8,  BASE_Y, oz+220, rng);

        // ── Checkpoints ──
        checkpoint(10, BASE_Y, oz+100, "§6§l✦ PONTO DE CONTROLE 1");
        checkpoint(48, BASE_Y, oz+175, "§6§l✦ PONTO DE CONTROLE 2");
        checkpoint(10, BASE_Y, oz+275, "§6§l✦ PONTO DE CONTROLE 3");

        // ── Arena final ──
        arenaFinalDragao(30, BASE_Y, oz+325);

        iluminacaoNether(oz, 340);
        processarFila();
    }

    // ═══════════════════════════════════════════════════════════════
    // ESTRUTURAS ─ CIDADE
    // ═══════════════════════════════════════════════════════════════

    private void predio(int ox, int oy, int oz, int larg, int alt, int prof,
                        Random rng, Material mat, Material vidro) {
        // Fundação
        for (int x = ox; x < ox+larg; x++)
            for (int z = oz; z < oz+prof; z++)
                bloco(x, oy-1, z, Material.STONE_BRICK_SLAB);

        // Paredes + janelas
        for (int y = oy; y < oy+alt; y++)
            for (int x = ox; x < ox+larg; x++)
                for (int z = oz; z < oz+prof; z++) {
                    boolean borda = x==ox || x==ox+larg-1 || z==oz || z==oz+prof-1;
                    if (!borda) continue;
                    boolean janela = y > oy && y < oy+alt-1 && y%2==0
                            && x!=ox && x!=ox+larg-1 && z!=oz && z!=oz+prof-1;
                    if (janela && rng.nextFloat() < 0.55f)
                        bloco(x, y, z, rng.nextFloat()<0.3f ? Material.GLASS : vidro);
                    else
                        bloco(x, y, z, rng.nextFloat()<0.08f ? Material.CRACKED_STONE_BRICKS : mat);
                }

        // Telhado com parapeito
        for (int x = ox; x < ox+larg; x++)
            for (int z = oz; z < oz+prof; z++) {
                bloco(x, oy+alt, z, Material.STONE_BRICK_SLAB);
                if (x==ox||x==ox+larg-1||z==oz||z==oz+prof-1)
                    bloco(x, oy+alt+1, z, Material.STONE_BRICK_WALL);
            }

        // Danos aleatórios
        for (int i = 0; i < (int)(larg*alt*0.06f); i++)
            bloco(ox+rng.nextInt(larg), oy+1+rng.nextInt(alt-1), oz+rng.nextInt(prof), Material.AIR);

        // Entrada
        int px = ox+larg/2;
        bloco(px,   oy,   oz, Material.AIR); bloco(px,   oy+1, oz, Material.AIR);
        bloco(px-1, oy,   oz, Material.AIR); bloco(px-1, oy+1, oz, Material.AIR);
        for (int i = 0; i < 3; i++) bloco(px, oy-1-i, oz-i-1, Material.STONE_BRICK_STAIRS);

        // Lanterna
        bloco(ox+larg/2, oy+alt-1, oz-1, Material.LANTERN);
    }

    private void praca(int ox, int oy, int oz) {
        for (int x = ox-9; x <= ox+9; x++)
            for (int z = oz-9; z <= oz+9; z++) {
                double d = Math.sqrt(Math.pow(x-ox,2)+Math.pow(z-oz,2));
                if (d <= 9) bloco(x, oy, z, d>7 ? Material.STONE_BRICK_SLAB : Material.CHISELED_STONE_BRICKS);
            }
        // Estátua central (pilar com topo quebrado)
        for (int y = oy+1; y <= oy+6; y++) bloco(ox, y, oz, Material.STONE_BRICKS);
        bloco(ox, oy+7, oz, Material.CRACKED_STONE_BRICKS);
        bloco(ox, oy+8, oz, Material.STONE_BRICK_WALL);
        // Bancos
        for (int i = 0; i < 4; i++) {
            int bx = (int)(ox+Math.cos(Math.PI/2*i)*5);
            int bz = (int)(oz+Math.sin(Math.PI/2*i)*5);
            bloco(bx, oy+1, bz, Material.STONE_BRICK_SLAB);
            bloco(bx+1, oy+1, bz, Material.STONE_BRICK_SLAB);
        }
        bloco(ox+3, oy+1, oz+3, Material.CAULDRON);
        bloco(ox-3, oy+1, oz-3, Material.CAULDRON);
    }

    private void fabrica(int ox, int oy, int oz, int larg, int alt, int prof) {
        for (int y = oy; y < oy+alt; y++)
            for (int x = ox; x < ox+larg; x++)
                for (int z = oz; z < oz+prof; z++) {
                    boolean borda = x==ox||x==ox+larg-1||z==oz||z==oz+prof-1;
                    if (borda) bloco(x, y, z, Material.BRICKS);
                }
        // Janelas industriais
        for (int z = oz+2; z < oz+prof-2; z += 5) {
            bloco(ox, oy+2, z, Material.IRON_BARS);
            bloco(ox, oy+3, z, Material.IRON_BARS);
        }
        // Telhado
        for (int x = ox; x < ox+larg; x++)
            for (int z = oz; z < oz+prof; z++)
                bloco(x, oy+alt, z, Material.SMOOTH_STONE_SLAB);
        // Chaminés
        for (int i = 0; i < larg/6; i++) {
            int cx = ox+3+i*6;
            for (int y = oy+alt; y <= oy+alt+5; y++) bloco(cx, y, oz+prof/2, Material.BRICKS);
            bloco(cx, oy+alt+6, oz+prof/2, Material.CAMPFIRE);
        }
        // Porta de carga
        for (int y = oy; y <= oy+3; y++)
            for (int x = ox+larg/2-2; x <= ox+larg/2+2; x++)
                bloco(x, y, oz, Material.AIR);
    }

    private void estacao(int ox, int oy, int oz, int larg, int alt) {
        // Plataforma
        for (int x = ox; x < ox+larg; x++)
            for (int z = oz; z < oz+12; z++)
                bloco(x, oy, z, Material.STONE_BRICKS);
        // Cobertura
        for (int x = ox; x < ox+larg; x++) {
            bloco(x, oy+alt, oz, Material.SMOOTH_STONE_SLAB);
            bloco(x, oy+alt, oz+11, Material.SMOOTH_STONE_SLAB);
            for (int z = oz; z < oz+12; z++) bloco(x, oy+alt, z, Material.IRON_BARS);
        }
        // Pilares
        for (int x = ox; x < ox+larg; x += 8) {
            bloco(x, oy+1, oz, Material.STONE_BRICKS);
            for (int y = oy+1; y <= oy+alt; y++) bloco(x, y, oz+11, Material.STONE_BRICKS);
        }
        // Trilhos
        for (int x = ox; x < ox+larg; x++) bloco(x, oy+1, oz+5, Material.RAIL);
        // Placa destruída
        bloco(ox+larg/2, oy+1, oz-1, Material.OAK_WALL_SIGN);
    }

    // ═══════════════════════════════════════════════════════════════
    // ESTRUTURAS ─ FLORESTA
    // ═══════════════════════════════════════════════════════════════

    private void arvoreMutante(int ox, int oy, int oz, int alt, Random rng) {
        Material tronco = rng.nextBoolean() ? Material.DARK_OAK_LOG : Material.OAK_LOG;
        Material folha  = rng.nextBoolean() ? Material.AZALEA_LEAVES : Material.DARK_OAK_LEAVES;

        int dx = 0, dz = 0;
        for (int y = oy; y < oy+alt; y++) {
            bloco(ox+dx, y, oz+dz, tronco);
            if (y%4==0 && rng.nextBoolean()) dx += rng.nextInt(3)-1;
            if (y%4==0 && rng.nextBoolean()) dz += rng.nextInt(3)-1;
        }
        // Galhos
        for (int g = 0; g < 2+rng.nextInt(3); g++) {
            int gy = oy+alt/2+rng.nextInt(alt/2);
            int gxF = ox+dx+rng.nextInt(7)-3, gzF = oz+dz+rng.nextInt(7)-3;
            for (int s = 0; s <= 4; s++)
                bloco(ox+dx+(gxF-ox-dx)*s/4, gy, oz+dz+(gzF-oz-dz)*s/4, tronco);
            for (int lx=-2;lx<=2;lx++) for (int ly=-1;ly<=1;ly++) for (int lz=-2;lz<=2;lz++)
                if (rng.nextFloat()>0.35f) bloco(gxF+lx, gy+ly, gzF+lz, folha);
        }
        // Copa
        int tx=ox+dx, tz=oz+dz, ty=oy+alt;
        for (int lx=-3;lx<=3;lx++) for (int ly=-1;ly<=3;ly++) for (int lz=-3;lz<=3;lz++)
            if (Math.abs(lx)+Math.abs(lz)+Math.abs(ly)<=4 && rng.nextFloat()>0.2f)
                bloco(tx+lx, ty+ly, tz+lz, folha);
        // Musgo na base
        for (int x=ox-1;x<=ox+1;x++) for (int z=oz-1;z<=oz+1;z++)
            if (rng.nextBoolean()) bloco(x, oy, z, Material.MOSS_BLOCK);
    }

    private void ponteSelva(int ox, int oy, int ozI, int ozF) {
        for (int z = ozI; z <= ozF; z++) {
            bloco(ox+1, oy, z, Material.JUNGLE_PLANKS);
            bloco(ox+2, oy, z, Material.JUNGLE_PLANKS);
            bloco(ox+3, oy, z, Material.JUNGLE_PLANKS);
            if (z%2==0) { bloco(ox, oy+1, z, Material.OAK_FENCE); bloco(ox+4, oy+1, z, Material.OAK_FENCE); }
            if (z%5==0) for (int d=1;d<=3;d++) bloco(ox+2, oy-d, z, Material.VINE);
        }
    }

    private void pantano(int ox, int oy, int oz, int larg, int prof, Random rng) {
        for (int x=ox;x<ox+larg;x++) for (int z=oz;z<oz+prof;z++) {
            float r = rng.nextFloat();
            if      (r<0.45f) bloco(x, oy-1, z, Material.WATER);
            else if (r<0.65f) bloco(x, oy, z, Material.LILY_PAD);
            else              bloco(x, oy, z, rng.nextBoolean()?Material.GRASS_BLOCK:Material.MOSS_BLOCK);
            if (r<0.08f) bloco(x, oy+1, z, Material.BROWN_MUSHROOM);
        }
    }

    private void ruinaAntiga(int ox, int oy, int oz, int larg, int alt, Random rng) {
        for (int x=ox;x<ox+larg;x++) for (int y=oy;y<oy+alt;y++) for (int z=oz;z<oz+larg;z++) {
            boolean borda = x==ox||x==ox+larg-1||z==oz||z==oz+larg-1;
            if (borda && rng.nextFloat()>0.25f) bloco(x, y, z, Material.MOSSY_STONE_BRICKS);
        }
        bloco(ox+larg/2, oy+1, oz+larg/2, Material.MOSSY_STONE_BRICKS);
        bloco(ox+larg/2, oy+2, oz+larg/2, Material.LECTERN);
    }

    private void tocaAranha(int ox, int oy, int oz, Random rng) {
        // Caverna semi-aberta coberta de teia
        for (int x=ox;x<ox+20;x++) for (int z=oz;z<oz+20;z++) {
            bloco(x, oy, z, Material.COBWEB);
            if (rng.nextFloat()<0.3f) bloco(x, oy+1, z, Material.COBWEB);
        }
        // Ovos (solo eggs)
        for (int i=0;i<8;i++) bloco(ox+2+rng.nextInt(16), oy+1, oz+2+rng.nextInt(16), Material.SCAFFOLDING);
        bloco(ox+10, oy+2, oz+10, Material.SPAWNER);
    }

    // ═══════════════════════════════════════════════════════════════
    // ESTRUTURAS ─ GUERRA
    // ═══════════════════════════════════════════════════════════════

    private void bunker(int ox, int oy, int oz, int larg, int alt, int prof) {
        for (int y=oy;y<oy+alt;y++) for (int x=ox;x<ox+larg;x++) for (int z=oz;z<oz+prof;z++) {
            boolean borda = x==ox||x==ox+larg-1||z==oz||z==oz+prof-1||y==oy||y==oy+alt-1;
            if (borda) bloco(x, y, z, Material.SMOOTH_STONE);
        }
        // Embrasuras
        for (int z=oz+2;z<oz+prof-2;z+=4) {
            bloco(ox, oy+2, z, Material.AIR);
            bloco(ox+larg-1, oy+2, z, Material.AIR);
        }
        // Entrada
        for (int y=oy;y<=oy+2;y++) for (int x=ox+larg/2-1;x<=ox+larg/2+1;x++) bloco(x, y, oz+prof-1, Material.AIR);
        bloco(ox+2, oy+1, oz+2, Material.CHEST);
        bloco(ox+larg-3, oy+1, oz+2, Material.BARREL);
        // Sacos de areia
        for (int x=ox;x<ox+larg;x++) bloco(x, oy, oz-1, Material.SAND);
    }

    private void trincheira(int ox, int oy, int oz, int larg, int prof) {
        for (int x=ox;x<ox+larg;x++) for (int d=0;d<prof;d++) for (int y=oy-d;y<=oy;y++) bloco(x, y, oz+d, Material.AIR);
        for (int x=ox;x<ox+larg;x++) bloco(x, oy-prof+1, oz+prof/2, Material.OAK_PLANKS);
        for (int x=ox+2;x<ox+larg-2;x+=5) {
            bloco(x, oy-1, oz+1, Material.OAK_LOG);
            bloco(x, oy-1, oz+prof-2, Material.OAK_LOG);
        }
    }

    private void veiculo(int ox, int oy, int oz, Random rng) {
        for (int x=ox;x<ox+6;x++) for (int z=oz;z<oz+4;z++) bloco(x, oy, z, Material.IRON_BLOCK);
        for (int x=ox+1;x<ox+5;x++) for (int z=oz;z<oz+3;z++) bloco(x, oy+1, z, Material.IRON_BLOCK);
        bloco(ox+2, oy+2, oz+1, Material.IRON_BARS);
        bloco(ox, oy, oz, Material.OAK_LOG); bloco(ox+5, oy, oz, Material.OAK_LOG);
        bloco(ox, oy, oz+3, Material.OAK_LOG); bloco(ox+5, oy, oz+3, Material.OAK_LOG);
        for (int i=0;i<8;i++) bloco(ox+rng.nextInt(6), oy+rng.nextInt(2), oz+rng.nextInt(4), Material.AIR);
        bloco(ox+3, oy+2, oz+1, Material.CAMPFIRE);
    }

    private void crateras(int ozI, int ozF, Random rng) {
        for (int i=0;i<(ozF-ozI)/12;i++) {
            int cx=5+rng.nextInt(50), cz=ozI+rng.nextInt(ozF-ozI), r=2+rng.nextInt(4);
            for (int x=cx-r;x<=cx+r;x++) for (int z=cz-r;z<=cz+r;z++) {
                if (Math.sqrt(Math.pow(x-cx,2)+Math.pow(z-cz,2))<=r) {
                    bloco(x, BASE_Y, z, Material.AIR);
                    bloco(x, BASE_Y-1, z, Material.GRAVEL);
                }
            }
        }
    }

    private void complexoIndustrial(int ox, int oy, int ozI, int ozF) {
        int alt = oy+10;
        for (int z=ozI;z<ozF;z++) for (int x=ox+10;x<ox+50;x++) if (z%2==0) bloco(x, alt, z, Material.SMOOTH_STONE_SLAB);
        for (int z=ozI;z<ozF;z+=10) for (int x : new int[]{ox+12,ox+30,ox+48}) {
            for (int y=oy;y<=alt;y++) bloco(x, y, z, Material.IRON_BLOCK);
            bloco(x, alt+1, z, Material.IRON_BARS);
        }
        for (int z=ozI+5;z<ozF-5;z+=15) {
            for (int x=ox+5;x<ox+55;x++) bloco(x, oy+6, z, Material.CHAIN);
        }
    }

    private void torreVigia(int ox, int oy, int oz, int alt) {
        for (int y=oy;y<oy+alt;y++) {
            bloco(ox,   y, oz,   Material.IRON_BLOCK);
            bloco(ox+4, y, oz,   Material.IRON_BLOCK);
            bloco(ox,   y, oz+4, Material.IRON_BLOCK);
            bloco(ox+4, y, oz+4, Material.IRON_BLOCK);
        }
        // Plataforma no topo
        for (int x=ox-1;x<=ox+5;x++) for (int z=oz-1;z<=oz+5;z++) bloco(x, oy+alt, z, Material.SMOOTH_STONE);
        // Grade
        for (int x=ox-1;x<=ox+5;x++) { bloco(x,oy+alt+1,oz-1,Material.IRON_BARS); bloco(x,oy+alt+1,oz+5,Material.IRON_BARS); }
        // Escada
        for (int y=oy;y<oy+alt;y++) bloco(ox-1, y, oz+2, Material.LADDER);
        bloco(ox+2, oy+alt+1, oz+2, Material.SPYGLASS); // detalhe decorativo
    }

    // ═══════════════════════════════════════════════════════════════
    // ESTRUTURAS ─ COVIL
    // ═══════════════════════════════════════════════════════════════

    private void pilar(int ox, int oy, int oz, int alt, Random rng) {
        for (int y=oy;y<oy+alt;y++) {
            int r = Math.max(1,(alt-(y-oy))/3);
            for (int dx=-r;dx<=r;dx++) for (int dz=-r;dz<=r;dz++)
                if (Math.abs(dx)+Math.abs(dz)<=r && rng.nextFloat()>0.15f)
                    bloco(ox+dx, y, oz+dz, Material.BLACKSTONE);
        }
        bloco(ox, oy+alt, oz, Material.OBSIDIAN);
        if (rng.nextBoolean()) bloco(ox, oy+alt+1, oz, Material.CRYING_OBSIDIAN);
    }

    private void ponteObsidiana(int ox, int oy, int oz, int xI, int larg) {
        for (int x=xI;x<xI+larg;x++) {
            bloco(x, oy, oz,   Material.OBSIDIAN);
            bloco(x, oy, oz+1, Material.OBSIDIAN);
            bloco(x, oy+1, oz-1, Material.BLACKSTONE_WALL);
            bloco(x, oy+1, oz+2, Material.BLACKSTONE_WALL);
        }
    }

    private void tunelLava(int ox, int oy, int ozI, int ozF) {
        for (int z=ozI;z<=ozF;z++) {
            for (int r=-4;r<=4;r++) for (int y=oy;y<=oy+6;y++) {
                double d = Math.sqrt(r*r+Math.pow(y-oy,2));
                if (d>=3.5 && d<=4.5) bloco(ox+8+r, y, z, Material.BLACKSTONE);
            }
            for (int x=ox+4;x<=ox+12;x++) bloco(x, oy, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
    }

    private void temploNether(int ox, int oy, int oz, int larg, int alt, int prof) {
        for (int x=ox;x<ox+larg;x++) for (int z=oz;z<oz+prof;z++) bloco(x, oy, z, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int y=oy+1;y<oy+alt;y++) for (int x=ox;x<ox+larg;x++) for (int z=oz;z<oz+prof;z++) {
            boolean borda = x==ox||x==ox+larg-1||z==oz||z==oz+prof-1;
            if (borda) bloco(x, y, z, Material.NETHER_BRICKS);
        }
        // Colunas internas
        for (int cx : new int[]{ox+4,ox+larg-5}) for (int cz : new int[]{oz+4,oz+prof-5})
            for (int y=oy+1;y<oy+alt-2;y++) bloco(cx, y, cz, Material.NETHER_BRICK_FENCE);
        // Altar
        for (int x=ox+larg/2-2;x<=ox+larg/2+2;x++) for (int z=oz+prof/2-2;z<=oz+prof/2+2;z++) bloco(x, oy+1, z, Material.CRYING_OBSIDIAN);
        bloco(ox+larg/2, oy+2, oz+prof/2, Material.RESPAWN_ANCHOR);
    }

    private void altar(int ox, int oy, int oz, Random rng) {
        for (int x=ox;x<ox+5;x++) for (int z=oz;z<oz+5;z++) bloco(x, oy, z, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int y=oy+1;y<=oy+3;y++) {
            bloco(ox, y, oz, Material.BLACKSTONE); bloco(ox+4, y, oz, Material.BLACKSTONE);
            bloco(ox, y, oz+4, Material.BLACKSTONE); bloco(ox+4, y, oz+4, Material.BLACKSTONE);
        }
        bloco(ox+2, oy+1, oz+2, rng.nextBoolean() ? Material.SOUL_LANTERN : Material.CRYING_OBSIDIAN);
    }

    private void arenaFinalDragao(int ox, int oy, int oz) {
        int raio = 30;
        for (int x=ox-raio;x<=ox+raio;x++) for (int z=oz-raio;z<=oz+raio;z++) {
            double d = Math.sqrt(Math.pow(x-ox,2)+Math.pow(z-oz,2));
            if (d<=raio) {
                bloco(x, oy, z, Material.END_STONE_BRICKS);
                bloco(x, oy-1, z, Material.BEDROCK);
            }
            if (d>raio-2.5 && d<=raio)
                for (int y=oy+1;y<=oy+7;y++) bloco(x, y, z, Material.OBSIDIAN);
        }
        // 4 pilares
        for (int i=0;i<4;i++) {
            int px=(int)(ox+Math.cos(Math.PI/2*i)*10), pz=(int)(oz+Math.sin(Math.PI/2*i)*10);
            for (int y=oy+1;y<=oy+8;y++) bloco(px, y, pz, Material.CRYING_OBSIDIAN);
            bloco(px, oy+9, pz, Material.END_ROD);
        }
        bloco(ox, oy+1, oz, Material.DRAGON_EGG);
        // Entrada
        for (int dz=oz-3;dz<=oz+3;dz++) for (int y=oy+1;y<=oy+6;y++) bloco(ox-raio, y, dz, Material.AIR);
    }

    // ═══════════════════════════════════════════════════════════════
    // ESTRUTURAS COMPARTILHADAS
    // ═══════════════════════════════════════════════════════════════

    private void checkpoint(int ox, int oy, int oz, String nome) {
        for (int x=ox-2;x<=ox+2;x++) for (int z=oz-2;z<=oz+2;z++) bloco(x, oy, z, Material.GOLD_BLOCK);
        for (int y=oy+1;y<=oy+4;y++) bloco(ox, y, oz, Material.BEACON);
        bloco(ox, oy+5, oz, Material.GOLD_BLOCK);
        bloco(ox+3, oy+1, oz, Material.LECTERN);
    }

    private void arenaChefe(int ox, int oy, int oz, int raio, Material piso, Material parede) {
        for (int x=ox-raio;x<=ox+raio;x++) for (int z=oz-raio;z<=oz+raio;z++) {
            double d = Math.sqrt(Math.pow(x-ox,2)+Math.pow(z-oz,2));
            if (d<=raio) { bloco(x, oy, z, piso); bloco(x, oy-1, z, parede); }
            if (d>raio-2 && d<=raio) for (int y=oy+1;y<=oy+5;y++) bloco(x, y, z, parede);
        }
        for (int dz=oz-2;dz<=oz+2;dz++) for (int y=oy+1;y<=oy+4;y++) bloco(ox-raio, y, dz, Material.AIR);
        bloco(ox, oy+1, oz-raio+2, Material.LECTERN);
    }

    // ═══════════════════════════════════════════════════════════════
    // DETALHES AMBIENTAIS
    // ═══════════════════════════════════════════════════════════════

    private void iluminacaoCidade(int oz, int prof) {
        for (int z=oz+8;z<oz+prof;z+=10) for (int x=5;x<60;x+=12) {
            bloco(x, BASE_Y+4, z, Material.LANTERN);
            bloco(x, BASE_Y+3, z, Material.IRON_BARS);
            bloco(x, BASE_Y+2, z, Material.IRON_BARS);
        }
    }

    private void iluminacaoFloresta(int oz, int prof) {
        for (int z=oz+10;z<oz+prof;z+=18) for (int x=12;x<50;x+=16) bloco(x, BASE_Y+2, z, Material.SOUL_LANTERN);
    }

    private void iluminacaoGuerra(int oz, int prof) {
        for (int z=oz+10;z<oz+prof;z+=14) for (int x=5;x<55;x+=15) bloco(x, BASE_Y+3, z, Material.SHROOMLIGHT);
    }

    private void iluminacaoNether(int oz, int prof) {
        for (int z=oz+12;z<oz+prof;z+=16) for (int x=5;x<55;x+=18) bloco(x, BASE_Y+5, z, Material.GLOWSTONE);
    }

    private void entulho(int oz, int prof, Random rng) {
        Material[] mats = {Material.GRAVEL,Material.COBBLESTONE,Material.STONE_BRICKS,Material.CRACKED_STONE_BRICKS,Material.DIRT};
        for (int i=0;i<280;i++) bloco(1+rng.nextInt(58), BASE_Y, oz+rng.nextInt(prof), mats[rng.nextInt(mats.length)]);
    }

    private void vegetacaoUrbana(int oz, int prof, Random rng) {
        for (int i=0;i<55;i++) bloco(rng.nextInt(60), BASE_Y, oz+rng.nextInt(prof), rng.nextBoolean()?Material.SHORT_GRASS:Material.FERN);
    }

    // ═══════════════════════════════════════════════════════════════
    // MOTOR DE FILA — processa blocos em lotes para não travar
    // ═══════════════════════════════════════════════════════════════

    /** Acumula um bloco — NÃO chama getBlockAt aqui */
    private void bloco(int x, int y, int z, Material mat) {
        fila.add(new int[]{x, y, z, mat.ordinal()});
    }

    /** Atalho para preencher camada horizontal */
    private void camada(int ox, int oy, int oz, int larg, int prof, Material mat) {
        for (int x=ox;x<ox+larg;x++) for (int z=oz;z<oz+prof;z++) bloco(x, oy, z, mat);
    }

    /**
     * Processa a fila em lotes de LOTE blocos por tick.
     * Nunca chama getType() — apenas setType(mat, false).
     * Chunks já foram pré-carregados em preCarregarChunks().
     */
    private void processarFila() {
        final List<int[]> snap = new ArrayList<>(fila);
        fila.clear();

        new BukkitRunnable() {
            int pos = 0;
            @Override public void run() {
                int fim = Math.min(pos+LOTE, snap.size());
                for (int i=pos;i<fim;i++) {
                    int[] b = snap.get(i);
                    mundo.getBlockAt(b[0], b[1], b[2]).setType(MATS[b[3]], false);
                }
                pos = fim;
                if (pos >= snap.size()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ═══════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ═══════════════════════════════════════════════════════════════

    public World getMundo()      { return mundo; }
    public World getLevelWorld() { return mundo; }
    public boolean isGenerated() { return mundo != null; }

    public Location getLevelSpawn(int nivel) {
        int oz = (nivel-1)*ESPACAMENTO;
        return new Location(mundo, 30, BASE_Y+1, oz+5);
    }

    public List<Location> getLevelCheckpoints(int nivel) {
        int oz = (nivel-1)*ESPACAMENTO;
        return switch (nivel) {
            case 1 -> List.of(new Location(mundo,27,BASE_Y+1,oz+92), new Location(mundo,27,BASE_Y+1,oz+205));
            case 2 -> List.of(new Location(mundo,27,BASE_Y+1,oz+105),new Location(mundo,27,BASE_Y+1,oz+215));
            case 3 -> List.of(new Location(mundo,27,BASE_Y+1,oz+100),new Location(mundo,27,BASE_Y+1,oz+190),new Location(mundo,27,BASE_Y+1,oz+265));
            case 4 -> List.of(new Location(mundo,10,BASE_Y+1,oz+100),new Location(mundo,48,BASE_Y+1,oz+175),new Location(mundo,10,BASE_Y+1,oz+275));
            default -> List.of(getLevelSpawn(nivel));
        };
    }

    public Location getBossArenaCenter(int nivel) {
        int oz = (nivel-1)*ESPACAMENTO;
        return switch (nivel) {
            case 1 -> new Location(mundo, 30, BASE_Y+1, oz+310);
            case 2 -> new Location(mundo, 30, BASE_Y+1, oz+290);
            case 3 -> new Location(mundo, 30, BASE_Y+1, oz+295);
            case 4 -> new Location(mundo, 30, BASE_Y+1, oz+325);
            default -> getLevelSpawn(nivel);
        };
    }
}
