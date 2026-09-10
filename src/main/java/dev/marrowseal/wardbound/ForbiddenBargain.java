package dev.marrowseal.wardbound;

import java.util.UUID;

/**
 * Rare post-ward cards. Offer validation stores explicit card IDs, so the deck
 * can grow beyond 31 cards without changing the chest state format again.
 */
public enum ForbiddenBargain {
    BORROWED_BREATH(0, "Borrowed Breath", Kind.DEBT,
            "Your next ordinary ward begins with one fewer life.", 0f, 15),
    IRON_DEBT(1, "Debt of Iron", Kind.DEBT,
            "The next ward you lose gains one extra grudge level.", 0f, 27),
    WATCHING_MARK(2, "The Watching Mark", Kind.DEBT,
            "Your next ordinary signed ward is made by the hand watching you.", 0f, 50),

    CRIMSON_BALANCE(3, "Crimson Balance", Kind.SCAR,
            "Strength I and Weakness I remain until another card reconciles them. Milk cannot settle the account.", 0.16f, 15),
    SEVERED_MEASURE(4, "Severed Measure", Kind.SCAR,
            "Lose one maximum heart. Your next two ordinary wards pay +25% loot and the measured clause holds each ward open three seconds longer.", 0.04f, 27),
    LAST_CANDLE(5, "The Last Candle", Kind.WAGER,
            "Your next ordinary minigame begins with exactly one life and its face is reversed, but its loot is +30%.", 0.00f, 34),
    GLASS_NERVE(6, "Glass Nerve", Kind.SCAR,
            "Speed I and Mining Fatigue I remain until your nerve is steadied by another card.", 0.14f, 50),
    PALE_COVENANT(7, "Pale Covenant", Kind.SCAR,
            "Resistance I and Hunger I remain until the covenant is broken by another card.", 0.16f, 66),
    OPEN_VEIN(8, "The Open Vein", Kind.SCAR,
            "Strength I remains, but one maximum heart is forfeit until a heart-returning card appears.", 0.12f, 82),
    THIN_BLOOD(9, "Thin Blood", Kind.SCAR,
            "Weakness I remains. Your next three ordinary wards pay +15% loot, but each carries one additional binding in its puzzle.", 0.04f, 97),
    LOADED_DICE(10, "Loaded Dice", Kind.WAGER,
            "For your next three ordinary wards: -1 starting life, +20% loot, and the minigame cadence runs slightly faster.", 0.00f, 38),
    MERCYS_DUE(11, "Mercy's Due", Kind.WAGER,
            "For your next two ordinary wards: +1 starting life, -15% loot, and the first significant error is forgiven.", 0.00f, 58),
    STILL_HEART(12, "The Still Heart", Kind.SCAR,
            "Regeneration I and Slowness I remain until another card wakes the heart.", 0.18f, 112),

    RECONCILED_BLOOD(13, "Reconciled Blood", Kind.REMEDY,
            "Remove Crimson Balance. Strength and weakness return to what they were.", 0.00f, 15),
    HEART_RETURNED(14, "Heart Returned", Kind.REMEDY,
            "Restore one maximum heart taken by a bargain.", 0.00f, 27),
    NERVE_SETTLED(15, "Nerve Settled", Kind.REMEDY,
            "Remove Glass Nerve. Its speed and fatigue both end.", 0.00f, 50),
    COVENANT_BROKEN(16, "Covenant Broken", Kind.REMEDY,
            "Remove Pale Covenant. Its resistance and hunger both end.", 0.00f, 66),
    HEART_AWAKENED(17, "Heart Awakened", Kind.REMEDY,
            "Remove The Still Heart. Its regeneration and slowness both end.", 0.00f, 112),
    DEBT_UNWRITTEN(18, "Debt Unwritten", Kind.REMEDY,
            "Erase one pending debt or lesser curse before it can be collected.", 0.00f, 30),
    THICKENED_BLOOD(19, "Thickened Blood", Kind.REMEDY,
            "Remove Thin Blood. Its lingering weakness ends; unspent ward bonuses remain yours.", 0.00f, 97),

    CROOKED_PRIVATE(20, "The Crooked Step", Kind.MASTER,
            "A private law from the crooked hand. While sprinting on solid ground, the world occasionally folds you a few safe blocks forward.", 0.00f),
    VEILED_PRIVATE(21, "The Veil Between", Kind.MASTER,
            "A private law from the veiled hand. Remain crouched and still for a moment to disappear briefly; the veil needs time before it answers again.", 0.00f),
    EXACTING_PRIVATE(22, "The Measured Stroke", Kind.MASTER,
            "A private law from the exacting hand. A fully readied melee strike, after a measured pause, lands with an additional half-stroke and reveals its target.", 0.00f),

    BLACK_DIVIDEND(23, "Grave Interest", Kind.EPIC,
            "The night keeps count. Every fifth hostile creature you kill after dusk repays you with health, hunger and experience.", 0.00f, 340),
    SECOND_LEDGER(24, "A Second Entry", Kind.EPIC,
            "Once each Minecraft day, lethal damage is crossed out. You remain at one heart under brief protection, followed by weakness.", 0.00f, 500),
    ABSOLUTION(25, "Absolution in Black Ink", Kind.EPIC,
            "Erase every standing bargain curse, scar, heart debt and lesser debt at once. Beneficial master laws and epic boons remain yours.", 0.00f, 645),

    BLOOD_TITHE(26, "Blood Tithe", Kind.CURSE,
            "One maximum heart is taken. While below half health, the blood it kept returns as Strength I.", 0.00f, 153),
    DIMINISHED_SHARE(27, "Diminished Share", Kind.CURSE,
            "Your next three ordinary wards pay -15% loot, but each begins with one additional life and exposes one reliable clue.", 0.00f, 180),
    FRAIL_HAND(28, "Brittle Pilgrimage", Kind.CURSE,
            "Fall damage is increased by 75%. Surviving a hard fall grants a brief burst of Speed.", 0.00f, 244),
    ASHEN_TONGUE(29, "Ashen Tongue", Kind.CURSE,
            "Weakness I clings to you for ten minutes. Hostile kills may shake loose experience; the chance rises as the mark nears expiry.", 0.00f, 308),

    REFRESH_HAND(30, "Refresh the Hand", Kind.REFRESH,
            "Burn this deal and draw again. The ward dislikes being asked twice.", 0.00f, 90),

    MOONLIT_HUNT(31, "The Moonlit Hunt", Kind.UNIQUE,
            "At night you move faster, but nearby hostile things are roused by the same law. The moon does not choose a side.", 0.00f, 600),
    RED_MARCH(32, "The Red March", Kind.UNIQUE,
            "Sprinting feeds on health instead of hunger. The road is cheaper to cross and more expensive to survive.", 0.00f, 627),
    FIRST_SUPPER(33, "The First Supper", Kind.UNIQUE,
            "The first food you finish each Minecraft day grants one unpredictable minute of borrowed excellence.", 0.00f, 664),
    LAST_WITNESS(34, "The Last Witness", Kind.UNIQUE,
            "Every seventh hostile kill prepares an echo. When a later hit would leave you below four hearts without killing you, the echo lifts the result back to four hearts once.", 0.00f, 709),

    EMBER_COUNT(35, "The Ember Count", Kind.WAGER,
            "Your next twelve melee hits ignite what they touch for four seconds.", 0.00f, 50),
    ORE_WHISPER(36, "Ore Whisper", Kind.WAGER,
            "For four minutes, nearby ore blocks betray themselves with cold sparks through the stone.", 0.00f, 80),
    BORROWED_MOMENTUM(37, "Borrowed Momentum", Kind.WAGER,
            "Speed II for three minutes. When the borrowed pace expires, Slowness I collects one minute in return.", 0.00f, 108),
    HUNTERS_DIVIDEND(38, "Hunter's Dividend", Kind.WAGER,
            "Your next ten hostile kills each repay two experience and one point of hunger. Every third payment briefly marks you.", 0.00f, 135),
    BELLGLASS_SIGHT(39, "Bellglass Sight", Kind.SCAR,
            "Night Vision remains until a remedy breaks the glass. In darkness, the same glass makes you glow.", 0.00f, 153),
    COAL_KISS(40, "Coal Kiss", Kind.WAGER,
            "Your next sixteen melee hits leave Weakness I on the target for four seconds.", 0.00f, 171),
    THORN_LEDGER(41, "The Thorn Ledger", Kind.SCAR,
            "Melee attackers are pricked for one damage. Projectiles against you deal twenty percent more until the ledger is unwritten.", 0.00f, 212),
    HEARTHMARK(42, "Hearthmark", Kind.WAGER,
            "Your next eight completed meals grant brief Regeneration. The eighth closes the hearth with thirty seconds of Hunger.", 0.00f, 260),
    SALT_CIRCLE(43, "Salt Circle", Kind.WAGER,
            "Your next eight undead kills grant brief Absorption. Other kills do not spend the circle.", 0.00f, 308),
    DUSTBOUND_SOLES(44, "Dustbound Soles", Kind.SCAR,
            "Fall damage is reduced by thirty percent, but Slowness I follows every step until a remedy cuts the binding.", 0.00f, 360),
    BLACK_COMPASS(45, "The Black Compass", Kind.WAGER,
            "For three minutes, hostile creatures within sixteen blocks are outlined through walls.", 0.00f, 440),
    LANTERN_BLOOD(46, "Lantern Blood", Kind.SCAR,
            "Night Vision remains. Below five hearts, the lantern turns inward and fills your sight with Darkness.", 0.00f, 500),
    IRON_ECHO(47, "Iron Echo", Kind.WAGER,
            "Your next ten fully readied melee strikes carry a violent extra knockback.", 0.00f, 560),
    GRAVE_RATION(48, "Grave Ration", Kind.WAGER,
            "Your next twelve hostile kills restore one point of hunger. If already full, the ration becomes one experience instead.", 0.00f, 609),
    POCKET_ECLIPSE(49, "A Pocket Eclipse", Kind.WAGER,
            "For three minutes, standing in low light repeatedly grants brief Invisibility. Bright places let the veil expire.", 0.00f, 645),
    PILGRIMS_LUCK(50, "Pilgrim's Luck", Kind.WAGER,
            "For your next thirty-two mined blocks, each break has a fifteen percent chance to shake loose an experience orb.", 0.00f, 682),

    MEMENTO_MORI(51, "Memento Mori", Kind.DEATH,
            "Two maximum hearts are buried. Hostile kills prepare pale reprieves; Death Resonance shortens the count from 20 to 18 to 16, and up to two reprieves may wait at once.", 0.00f, 900),
    BLACK_SUN(52, "The Black Sun", Kind.DEATH,
            "Daylight leaves you Weak and visibly marked. Night grants Strength II and Night Vision instead.", 0.00f, 900),
    COFFIN_ROAD(53, "The Coffin Road", Kind.DEATH,
            "Slowness I becomes permanent, but the ground can no longer hurt you when you fall onto it.", 0.00f, 967),
    GRAVE_BELL(54, "The Grave Bell", Kind.DEATH,
            "Hunger I never leaves. Hostile kills ring the bell; Death Resonance shortens the toll from 6 to 5 to 4 names, granting Absorption II and five experience each time.", 0.00f, 1056),

    SHATTER_BELLGLASS(55, "Shatter the Bellglass", Kind.REMEDY,
            "Break Bellglass Sight and end both its night vision and its answering glow.", 0.00f),
    PRUNE_THORNS(56, "Prune the Ledger", Kind.REMEDY,
            "Remove The Thorn Ledger and return projectile damage to its ordinary measure.", 0.00f),
    CUT_DUST_BINDING(57, "Cut the Dust Binding", Kind.REMEDY,
            "Remove Dustbound Soles. The fall bargain and its Slowness both end.", 0.00f),
    SNUFF_LANTERN(58, "Snuff the Lantern", Kind.REMEDY,
            "Remove Lantern Blood. The borrowed sight and low-health Darkness end together.", 0.00f),

    ROTTEN_LEDGER(59, "The Rotten Ledger", Kind.CONTRACT,
            "Contract: kill 25 zombies. Payment: Strength I for ten minutes and twelve experience. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 108),
    BONE_TALLY(60, "The Bone Tally", Kind.CONTRACT,
            "Contract: kill 20 skeletons. Payment: projectile damage is reduced by forty percent for fifteen minutes. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 117),
    CREEPER_CLAUSE(61, "The Creeper Clause", Kind.CONTRACT,
            "Contract: kill 12 creepers. Payment: explosion damage is reduced by sixty percent for fifteen minutes. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 135),
    SILK_WARRANT(62, "The Silk Warrant", Kind.CONTRACT,
            "Contract: kill 18 spiders. Payment: Speed II and Jump Boost I for twelve minutes. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 153),
    ENDER_AUDIT(63, "The Ender Audit", Kind.CONTRACT,
            "Contract: kill 8 Endermen. Payment: five Ender Steps that soften heavy incoming blows. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 212),
    WITCH_LEDGER(64, "The Witch Ledger", Kind.CONTRACT,
            "Contract: kill 10 witches. Payment: harmful effects are purged and magic damage is halved for twelve minutes. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 276),

    STONE_COMMUNION(65, "Stone Communion", Kind.RITUAL,
            "Ritual: break 128 stone-family blocks in low light. Payment: Haste II for ten minutes. Purity clause: breaking a target block outside the required condition scars the rite; an unscarred completion pays six extra experience and brief Luck, while one or two breaches still leave a legible rite worth three experience.", 0.00f, 180),
    VEIN_LITANY(66, "The Vein Litany", Kind.RITUAL,
            "Ritual: mine 32 ore blocks at Y 32 or below. Payment: Ore Whisper for twelve minutes. Purity clause: breaking a target block outside the required condition scars the rite; an unscarred completion pays six extra experience and brief Luck, while one or two breaches still leave a legible rite worth three experience.", 0.00f, 228),
    WOODEN_CONFESSION(67, "Wooden Confession", Kind.RITUAL,
            "Ritual: cut 96 logs beneath open sky. Payment: Speed I and Haste I for fifteen minutes. Purity clause: breaking a target block outside the required condition scars the rite; an unscarred completion pays six extra experience and brief Luck, while one or two breaches still leave a legible rite worth three experience.", 0.00f, 276),
    EARTHEN_VIGIL(68, "The Earthen Vigil", Kind.RITUAL,
            "Ritual: break 96 earth, mud, sand, gravel or clay blocks beneath an open night sky. Payment: Absorption II and Resistance I for ten minutes. Purity clause: breaking a target block outside the required condition scars the rite; an unscarred completion pays six extra experience and brief Luck, while one or two breaches still leave a legible rite worth three experience.", 0.00f, 324),
    OBSIDIAN_PRAYER(69, "Obsidian Prayer", Kind.RITUAL,
            "Ritual: mine 12 obsidian blocks in strong local light. Payment: Fire Resistance for thirty minutes and Strength I for ten. Purity clause: breaking a target block outside the required condition scars the rite; an unscarred completion pays six extra experience and brief Luck, while one or two breaches still leave a legible rite worth three experience.", 0.00f, 400),

    RED_CENSUS(70, "The Red Census", Kind.COVENANT,
            "Covenant: kill 20 villagers. Payment: six ordinary wards gain +20% loot and you wear a false hero's blessing for twenty minutes. Completion permanently deepens Covenant Stain and increases Death-hand pressure.", 0.00f, 420),
    IRON_SILENCE(71, "The Iron Silence", Kind.COVENANT,
            "Covenant: kill 5 iron golems. Payment: Resistance II for fifteen minutes; Mining Fatigue I is written into the same payment. Completion permanently deepens Covenant Stain and increases Death-hand pressure.", 0.00f, 500),
    INNOCENCE_TAX(72, "The Innocence Tax", Kind.COVENANT,
            "Covenant: kill 40 passive animals. Payment: Regeneration II for ten minutes, accompanied by Hunger I. Completion permanently deepens Covenant Stain and increases Death-hand pressure.", 0.00f, 580),
    WITCHFIRE_TITHE(73, "Witchfire Tithe", Kind.COVENANT,
            "Covenant: kill 12 witches. Payment: one lethal blow may be refused, twenty experience is paid, and Weakness I follows for ten minutes. Completion permanently deepens Covenant Stain and increases Death-hand pressure.", 0.00f, 636),

    VEIN_DRINKER(74, "Vein Drinker", Kind.WAGER,
            "Your next eight hostile kills each return one heart and raise a brief red haze around you.", 0.00f, 292),
    QUICKSILVER_PRAYER(75, "Quicksilver Prayer", Kind.WAGER,
            "For two minutes your limbs answer quickly: Speed I and Haste I remain. When the prayer ends, Weakness I is collected for thirty seconds.", 0.00f, 380),
    FERRYMAN_LEDGER(76, "The Ferryman's Ledger", Kind.WAGER,
            "The next five heavy blows against you are reduced. Each spared blow is crossed out in ash.", 0.00f, 480),
    CINDER_VOW(77, "Cinder Vow", Kind.UNIQUE,
            "Flame harms you less. Your melee strikes brand what they touch for a moment.", 0.00f, 755),
    SHIVERING_TITHE(78, "Shivering Tithe", Kind.CURSE,
            "Haste I clings to you, but Hunger I clings with it until the tithe is thawed.", 0.00f, 340),
    THAW_THE_TITHE(79, "Thaw the Tithe", Kind.REMEDY,
            "End Shivering Tithe. Both its haste and its hunger leave together.", 0.00f, 340),
    GRIM_HARVEST(80, "The Grim Harvest", Kind.CONTRACT,
            "Contract: kill 30 hostile creatures. Payment: Vein Drinker is written for eight kills and eight experience is paid. Swift clause: finish within ten minutes while sustaining a long enough streak for four extra experience; an exceptional six-minute pace with a stronger streak doubles that clause payment.", 0.00f, 340),
    SAND_LITURGY(81, "Sand Liturgy", Kind.RITUAL,
            "Ritual: break 96 sand, sandstone or glass blocks in direct daylight. Payment: Fire Resistance and Speed I for ten minutes. Purity clause: breaking a target block outside the required condition scars the rite; an unscarred completion pays six extra experience and brief Luck, while one or two breaches still leave a legible rite worth three experience.", 0.00f, 460),
    OSSUARY_VOW(82, "The Ossuary Vow", Kind.COVENANT,
            "Covenant: kill 24 undead. Payment: ten ordinary wards gain +10% loot, but Hunger I marks the vow for six minutes. Completion permanently deepens Covenant Stain and increases Death-hand pressure.", 0.00f, 682),

    COLD_LEDGER(83, "The Cold Ledger", Kind.WAGER,
            "Your next eight hostile kills each pay one experience; every fourth payment grants a short burst of Speed.", 0.00f, 40),
    HOLLOW_LANTERN(84, "The Hollow Lantern", Kind.SCAR,
            "Night Vision remains, but direct daylight writes Weakness I across the same page until the lantern is extinguished.", 0.00f, 99),
    SNUFF_HOLLOW(85, "Extinguish the Hollow", Kind.REMEDY,
            "Remove The Hollow Lantern. Its borrowed sight and daylight weakness end together.", 0.00f, 99),
    PALE_RATION(86, "The Pale Ration", Kind.WAGER,
            "Your next eight completed meals grant brief Absorption. The eighth closes the ration with twenty seconds of Hunger.", 0.00f, 162),
    WARD_OF_BONE(87, "The Ward of Bone", Kind.SCAR,
            "Below half health, Resistance I answers you. Above half health, Mining Fatigue I remains until the bone ward is broken.", 0.00f, 260),
    BREAK_BONE_WARD(88, "Break the Bone Ward", Kind.REMEDY,
            "Remove The Ward of Bone and end both halves of its health-bound bargain.", 0.00f, 260),
    SILENT_DIVIDEND(89, "The Silent Dividend", Kind.WAGER,
            "Your next twelve hostile kills are counted in silence. Every third payment grants two experience and brief Invisibility.", 0.00f, 440),
    DROWNED_NAME(90, "The Drowned Name", Kind.UNIQUE,
            "Water never takes your breath. While submerged you move faster; standing in direct daylight on dry ground makes the borrowed name answer with Weakness.", 0.00f, 655),
    BLACK_REPRIEVE(91, "The Black Reprieve", Kind.EPIC,
            "Once each Minecraft day, a nonlethal blow that leaves you below three hearts grants brief Resistance and Absorption. It does not erase the damage already dealt.", 0.00f, 764),
    HOUSE_OF_ASH(92, "The House of Ash", Kind.UNIQUE,
            "Fire harms you less. While you are actually burning, Strength I answers the flame; the bargain ends only if a law specifically unwrites it.", 0.00f, 864),

    WRONG_DOOR(93, "The Wrong Door", Kind.WAGER,
            "The world misfiles your position and returns you roughly two hundred blocks away, on the nearest safe surface it can find.", 0.00f, 27),
    TEN_SECONDS_UNWRITTEN(94, "Ten Seconds Unwritten", Kind.WAGER,
            "For ten seconds damage is not entered into the ledger at all. When the line returns, it returns normally.", 0.00f, 38),
    VILLAGE_COLD_SHOULDER(95, "The Village Cold Shoulder", Kind.WAGER,
            "For three minutes nearby villagers refuse your presence and flee when you approach.", 0.00f, 46),
    IRON_ACCUSATION(96, "Iron Accusation", Kind.WAGER,
            "For two minutes nearby iron golems treat you as the thing the village was built to stop.", 0.00f, 74),
    WARDENS_BLIND_SPOT(97, "The Warden's Blind Spot", Kind.WAGER,
            "For four minutes Wardens repeatedly lose you as a target, even after they notice you.", 0.00f, 171),
    EMPTY_DEEP(98, "The Empty Deep", Kind.WAGER,
            "For four minutes new Wardens cannot enter the world close to you. Existing Wardens remain real.", 0.00f, 212),
    ZOMBIE_ARMISTICE(99, "Rotten Armistice", Kind.WAGER,
            "For two minutes zombies and their close kin refuse to keep you as a target.", 0.00f, 34),
    BONE_TRUCE(100, "The Bone Truce", Kind.WAGER,
            "For two minutes skeletons and their close kin refuse to keep you as a target.", 0.00f, 50),
    CREEPER_COURTESY(101, "Creeper Courtesy", Kind.WAGER,
            "For two minutes creepers forget that approaching you is a form of conversation.", 0.00f, 66),
    SPIDER_TREATY(102, "The Eight-Legged Treaty", Kind.WAGER,
            "For two minutes spiders repeatedly drop you from their target list.", 0.00f, 82),
    ENDER_AMNESTY(103, "Ender Amnesty", Kind.WAGER,
            "For three minutes Endermen refuse to hold a grievance against you.", 0.00f, 135),
    WITCHS_PRIVILEGE(104, "A Witch's Privilege", Kind.WAGER,
            "For three minutes witches cease treating you as a valid combat target.", 0.00f, 162),
    HOSTILE_CENSUS(105, "The Hostile Census", Kind.WAGER,
            "For two minutes every hostile creature near you is outlined. The census outlines you as well.", 0.00f, 42),
    FIRE_WITHOUT_FUEL(106, "Fire Without Fuel", Kind.WAGER,
            "Every hostile creature currently close to you catches fire for ten seconds. You do not.", 0.00f, 58),
    WRONG_GRAVITY(107, "A Small Error in Gravity", Kind.WAGER,
            "The floor rejects you briefly, then remembers you slowly: Levitation followed by Slow Falling.", 0.00f, 21),
    HUNDRED_STEPS(108, "One Hundred Impossible Steps", Kind.WAGER,
            "Speed IV for thirty seconds. The next thirty seconds are collected as Slowness I.", 0.00f, 24),
    RED_MINUTE(109, "The Red Minute", Kind.WAGER,
            "For one minute Strength II and Speed I answer together, while Hunger I keeps the clock honest.", 0.00f, 30),
    GLASS_SAINT(110, "The Glass Saint", Kind.WAGER,
            "For thirty seconds projectiles cannot hurt you, but every non-projectile wound is twenty-five percent worse.", 0.00f, 90),
    SHARED_PAIN(111, "Shared Pain", Kind.WAGER,
            "For two minutes melee attackers receive thirty percent of the damage they successfully deal to you.", 0.00f, 104),
    MEASURED_MERCY(112, "Three Measured Mercies", Kind.WAGER,
            "The next three otherwise-lethal hits leave you at one heart instead. Each mercy is consumed separately.", 0.00f, 117),
    TIDAL_BODY(113, "The Tidal Body", Kind.WAGER,
            "For three minutes water becomes easier than air: Water Breathing and Dolphin's Grace.", 0.00f, 40),
    CHORUS_ERROR(114, "Chorus Error", Kind.WAGER,
            "For two minutes the world randomly nudges you to a nearby safe position every twenty seconds.", 0.00f, 122),
    RETURN_ADDRESS(115, "Return Address", Kind.WAGER,
            "Your current position is written down. Two minutes later the page attempts to return you there.", 0.00f, 130),
    LANTERN_BREAK(116, "Break the Lantern", Kind.WAGER,
            "Darkness takes your sight for twenty seconds. When it leaves, Night Vision remains for three minutes.", 0.00f, 54),
    WHITE_NOISE(117, "White Noise", Kind.WAGER,
            "Every harmful potion effect is stripped away at once. Nausea remains for ten seconds as the eraser mark.", 0.00f, 62),
    BLOOD_MONEY(118, "Blood Money", Kind.WAGER,
            "Six health is paid immediately, never below one heart. Fifteen experience is paid back.", 0.00f, 70),
    COIN_EDGE(119, "The Coin Lands on Its Edge", Kind.WAGER,
            "The card chooses between a generous minute and a malicious minute. It does not tell you which first.", 0.00f, 80),
    CHANCE_ENGINE(120, "The Chance Engine", Kind.WAGER,
            "One of eight short laws is selected at random: speed, strength, haste, resistance, invisibility, jump, hunger or weakness.", 0.00f, 108),
    BLACKOUT(121, "Blackout Census", Kind.WAGER,
            "Darkness follows you for forty-five seconds, but hostile creatures nearby shine through it.", 0.00f, 94),
    AIR_BORROWED(122, "Borrowed Air", Kind.WAGER,
            "Jump Boost IV for twenty seconds and Slow Falling for the following minute.", 0.00f, 101),
    BURNING_PACT(123, "The Burning Pact", Kind.WAGER,
            "You ignite for five seconds. Fire Resistance then remains for three minutes.", 0.00f, 112),
    FROSTED_BLOOD(124, "Frosted Blood", Kind.WAGER,
            "Resistance II and Slowness II share your body for two minutes.", 0.00f, 119),
    NIGHT_PASS(125, "Night Pass", Kind.WAGER,
            "For four minutes Night Vision remains; while it is actually night, Speed I joins it.", 0.00f, 126),
    DAY_PASS(126, "Day Pass", Kind.WAGER,
            "For four minutes, direct daylight repeatedly grants Haste II and Speed I.", 0.00f, 133),
    HOLLOW_SKIN(127, "Hollow Skin", Kind.WAGER,
            "For ninety seconds you are both Invisible and Glowing. Most eyes disagree about which statement matters.", 0.00f, 140),
    GRAVE_SILENCE(128, "Grave Silence", Kind.WAGER,
            "For two minutes ordinary undead repeatedly forget you as a combat target.", 0.00f, 148),
    BEAST_MARCH(129, "The Beast March", Kind.WAGER,
            "For ninety seconds nearby passive animals attempt to follow you, whether or not you are holding food.", 0.00f, 155),
    GOLEM_ESCORT(130, "Iron Escort", Kind.WAGER,
            "For two minutes nearby iron golems are repeatedly pointed toward hostile creatures near you.", 0.00f, 166),
    GOLEM_ENMITY(131, "Iron Enmity", Kind.WAGER,
            "For two minutes nearby iron golems are repeatedly pointed toward you.", 0.00f, 176),
    VILLAGE_EXILE(132, "Village Exile", Kind.SCAR,
            "Villagers flee you and nearby iron golems enforce the exile indefinitely. Village Pardon is the clean way back in.", 0.00f, 196),
    VILLAGE_PARDON(133, "Village Pardon", Kind.REMEDY,
            "Immediately ends every temporary Wardbound village or iron-golem hostility written by these cards.", 0.00f, 196),
    CREEPER_BLESSING(134, "Creeper's Blessing", Kind.WAGER,
            "For three minutes creepers ignore you and explosion damage against you is reduced by half.", 0.00f, 206),
    FALLING_CROWN(135, "The Falling Crown", Kind.WAGER,
            "For two minutes fall damage is refused and Jump Boost II remains.", 0.00f, 218),
    STONE_SKIN(136, "Stone Skin, Stone Pace", Kind.WAGER,
            "Resistance II and Slowness I remain together for two minutes.", 0.00f, 238),
    OPEN_SKY(137, "Open Sky Clause", Kind.WAGER,
            "For two minutes, while the sky is visible, Speed II and Slow Falling are renewed.", 0.00f, 250),
    DEEP_BREATH(138, "Deep Breath", Kind.WAGER,
            "For three minutes below Y 32, Haste II and Night Vision are renewed; above it, the card is silent.", 0.00f, 263),

    FOURTH_CARD(139, "The Fourth Card", Kind.WAGER,
            "The next ordinary deck receives one additional legal card if the screen can hold it.", 0.00f, 30),
    FIFTH_CARD(140, "The Fifth Finger", Kind.WAGER,
            "The next ordinary deck attempts to expose two additional cards. Attention rises by two for the indulgence.", 0.00f, 162),
    NARROW_HAND(141, "Narrow Hand, Better Ink", Kind.WAGER,
            "The next ordinary deck loses one card, but the remaining hand is pushed toward higher-quality unlocked shelves.", 0.00f, 50),
    CURSE_DAMPER(142, "Salt Between the Pages", Kind.WAGER,
            "The next two ordinary decks sharply reduce their chance to become Curse hands.", 0.00f, 153),
    CURSE_BAIT(143, "Red Invitation", Kind.WAGER,
            "The next ordinary deck gains one card but becomes much more likely to open on the Curse shelf.", 0.00f, 153),
    RARE_INK(144, "Rare Ink", Kind.WAGER,
            "The next ordinary deck leans harder toward unlocked Epic, Unique and Covenant shelves.", 0.00f, 117),
    CLEAN_MARGIN(145, "A Clean Margin", Kind.WAGER,
            "The next ordinary hand refuses Debt, Scar and Curse cards and fills from legal alternatives.", 0.00f, 135),
    BLACK_MARGIN(146, "The Black Margin", Kind.WAGER,
            "The next ordinary hand refuses Remedies and leans toward Wagers and higher unlocked shelves.", 0.00f, 153),
    NO_ECHOES(147, "No Echoes", Kind.WAGER,
            "For the next three ordinary decks, recently offered cards are treated as if their ink has not yet dried enough to return.", 0.00f, 171),
    REMEDY_WITNESS(148, "A Remedy Must Witness", Kind.WAGER,
            "If you carry a removable burden, the next ordinary deck is forced to include one legal Remedy.", 0.00f, 196),
    DEALERS_FOURTH(149, "The Dealer's Fourth Finger", Kind.WAGER,
            "Your next physical Sealed Card audience receives one additional dealer card when possible.", 0.00f, 228),
    GOLDEN_CUT(150, "The Golden Cut", Kind.WAGER,
            "The next two ordinary decks each lose one low-priority card and attempt to replace it with stronger unlocked ink.", 0.00f, 260),
    DEEP_SHELF(151, "The Deep Shelf", Kind.WAGER,
            "The next ordinary deck must contain an Epic or Unique if one is unlocked and legally available; otherwise it takes the strongest Wager it can find.", 0.00f, 308),
    CURSE_LULL(152, "Three Quiet Hands", Kind.WAGER,
            "The next three ordinary decks greatly reduce Curse-hand pressure without changing Death pressure.", 0.00f, 340),

    REGISTRY_LOTTERY(153, "The Registry Lottery", Kind.WAGER,
            "One registered item is chosen from the game's entire item registry. Vanilla, modded, spawn egg, junk or treasure: the card does not distinguish them.", 0.00f, 50),
    EGG_WITH_NO_SHELL(154, "An Egg With No Shell", Kind.WAGER,
            "One random registered spawn egg is placed in your inventory. The card refuses to identify the creature first.", 0.00f, 99),
    SIXTY_FOURTH_STEP(155, "The Sixty-Fourth Step", Kind.WAGER,
            "The world attempts to place you roughly sixty-four blocks away on a safe surface. Direction is not part of the contract.", 0.00f, 34),
    STORM_RECEIPT(156, "The Storm Receipt", Kind.WAGER,
            "Several nearby hostile creatures are struck by lightning. You receive a brief fireproof receipt for standing too close to the accounting.", 0.00f, 70),
    BORROWED_FACE(157, "Borrowed Face", Kind.WAGER,
            "You vanish for one minute while a nearby living thing is outlined as though it were the person the world meant to notice.", 0.00f, 90),
    MOB_EXCHANGE(158, "Exchange of Positions", Kind.WAGER,
            "If a hostile creature is close enough, you and it exchange positions without asking either participant.", 0.00f, 126),
    BAD_RECEIPT(159, "A Bad Receipt", Kind.WAGER,
            "Twenty experience is paid immediately. One harmful effect is selected as the processing fee.", 0.00f, 60),
    LUCKY_POCKET(160, "The Lucky Pocket", Kind.WAGER,
            "Three random registered items are produced. Ninety seconds of Hunger and Unluck follow the impossible inventory audit.", 0.00f, 80),
    STRANGE_INVITATION(161, "A Strange Invitation", Kind.WAGER,
            "The next two ordinary hands become substantially more likely to receive an extra Anomaly card.", 0.00f, 99),
    CERTAINLY_NOTHING(162, "Certainly Nothing", Kind.WAGER,
            "The card promises that nothing happens. It has a documented history of being inaccurate.", 0.00f, 22),
    FOURTH_SHAPE(163, "A Fourth Shape", Kind.WAGER,
            "The next ordinary hand is guaranteed to receive one extra Anomaly card if the table has room.", 0.00f, 162),
    WHITE_THREAD(164, "The White Thread", Kind.WAGER,
            "The next Anomaly is restrained: severe curse and catastrophic outcomes are removed from its possible resolutions.", 0.00f, 180),

    BLOODWELL_REFLEX(171, "Bloodwell Reflex", Kind.WAGER,
            "For four minutes, wounds renew faster below half health and faster still below one quarter. Repeated signatures teach the reflex to remain.", 0.00f, 30),
    LONG_HAND(172, "The Long Hand", Kind.WAGER,
            "For three minutes, block interaction reach extends by two blocks. Revisions stretch the hand farther; a Palimpsest stops retracting it.", 0.00f, 60),
    HARVEST_SHARE(173, "The Harvest Share", Kind.WAGER,
            "For five minutes, mature crops pay roughly twenty-five percent additional harvest. Revisions increase the share before the final clause becomes permanent.", 0.00f, 40),
    BALLISTIC_SCRIPT(174, "Ballistic Script", Kind.WAGER,
            "For four minutes, projectile damage is increased by ten percent. Repeated signatures revise the margin to fifteen and then twenty percent.", 0.00f, 50),
    FAR_LEDGER(175, "The Far Ledger", Kind.WAGER,
            "For four minutes, projectile damage gains additional interest with distance, reaching its full bonus only on long shots.", 0.00f, 99),
    CROWD_INTEREST(176, "Crowd Interest", Kind.WAGER,
            "For three minutes, nearby hostile creatures raise your outgoing damage. The rate and the number of counted threats increase with revision.", 0.00f, 126),
    IRON_PULSE(177, "The Iron Pulse", Kind.WAGER,
            "For four minutes, armor rises in steps as your health falls. Revisions harden each threshold; the final rewritten pulse remains indefinitely.", 0.00f, 144),
    SCRIBBLED_RESPITE(178, "Scribbled Respite", Kind.WAGER,
            "For the next three ordinary wards, each minigame receives three additional seconds before the seal closes.", 0.00f, 108),
    PALE_MARGIN(179, "The Pale Margin", Kind.CURSE,
            "Every minigame permanently gains two additional seconds, but one heart is written into debt as the price of that mercy.", 0.00f, 244),

    PINHOLE_DOCTRINE(180, "Pinhole Doctrine", Kind.WAGER,
            "Projectile damage rises on shots made from at least twelve blocks away. Revisions deepen the long-range margin.", 0.00f, 50),
    POINT_BLANK_RECEIPT(181, "Point-Blank Receipt", Kind.WAGER,
            "Projectile damage rises sharply inside six blocks. Revisions reward the dangerous distance more heavily.", 0.00f, 60),
    HIGH_ARC_CLAUSE(182, "High Arc Clause", Kind.WAGER,
            "Projectiles deal additional damage when fired from at least three blocks above the target.", 0.00f, 80),
    UPWARD_INTEREST(183, "Upward Interest", Kind.WAGER,
            "Projectiles deal additional damage when the target stands at least three blocks above you.", 0.00f, 90),
    FIRST_VOLLEY(184, "The First Volley", Kind.WAGER,
            "Projectiles strike harder against targets that are still near full health.", 0.00f, 108),
    LAST_BOLT(185, "The Last Bolt", Kind.WAGER,
            "Projectiles strike harder against targets already below one third health.", 0.00f, 126),
    SOLITARY_MARK(186, "The Solitary Mark", Kind.WAGER,
            "Projectiles gain damage against targets with almost no hostile company nearby.", 0.00f, 144),
    RUNNING_SIGHT(187, "Running Sight", Kind.WAGER,
            "Projectiles gain damage while you are sprinting when the shot lands.", 0.00f, 162),
    STILL_HAND(188, "The Still Hand", Kind.WAGER,
            "Projectiles gain damage while you are nearly motionless when the shot lands.", 0.00f, 180),
    PIERCED_CROWD(189, "Pierced Crowd", Kind.WAGER,
            "Projectiles gain stacking damage when the target is surrounded by other hostile creatures.", 0.00f, 228),
    RED_KNUCKLE(190, "The Red Knuckle", Kind.WAGER,
            "Melee damage rises while you are below half health.", 0.00f, 60),
    OPENING_CUT(191, "The Opening Cut", Kind.WAGER,
            "Melee strikes hit harder against targets still near full health.", 0.00f, 80),
    EXECUTIONERS_MARGIN(192, "Executioner's Margin", Kind.WAGER,
            "Melee strikes hit harder against targets below one third health.", 0.00f, 99),
    LONE_DUEL(193, "The Lone Duel", Kind.WAGER,
            "Melee damage rises when almost no other hostile creature is near you.", 0.00f, 117),
    PRESSED_BLADE(194, "The Pressed Blade", Kind.WAGER,
            "Melee damage rises when four or more hostile creatures crowd around you.", 0.00f, 135),
    HIGH_GROUND(195, "High Ground Clause", Kind.WAGER,
            "Melee damage rises when you stand distinctly above the target.", 0.00f, 153),
    LOW_ROAD(196, "The Low Road", Kind.WAGER,
            "Melee damage rises when the target stands distinctly above you.", 0.00f, 171),
    RUNNING_HAND(197, "The Running Hand", Kind.WAGER,
            "Melee damage rises while sprinting.", 0.00f, 196),
    STILL_POINT(198, "The Still Point", Kind.WAGER,
            "Melee damage rises while your feet are nearly still.", 0.00f, 228),
    SECOND_WOUND(199, "The Second Wound", Kind.WAGER,
            "Melee damage rises against enemies that are already wounded but not yet close to death.", 0.00f, 260),
    ASH_PLATE(200, "Ash Plate", Kind.WAGER,
            "Incoming damage is reduced while you remain above seventy percent health.", 0.00f, 70),
    LAST_PLATE(201, "The Last Plate", Kind.WAGER,
            "Incoming damage is reduced sharply below thirty-five percent health.", 0.00f, 90),
    ARROW_LEDGER(202, "The Arrow Ledger", Kind.WAGER,
            "Incoming projectile damage is reduced.", 0.00f, 108),
    CLOSE_SEAL(203, "The Close Seal", Kind.WAGER,
            "Incoming direct creature attacks are reduced.", 0.00f, 126),
    CROWD_SHELTER(204, "Crowd Shelter", Kind.WAGER,
            "Incoming damage is reduced when four or more hostiles crowd around you.", 0.00f, 144),
    SOLITARY_WARD(205, "The Solitary Ward", Kind.WAGER,
            "Incoming damage is reduced while one or fewer hostiles stand near you.", 0.00f, 162),
    FALLING_INK(206, "Falling Ink", Kind.WAGER,
            "Fall damage is reduced. Revisions make the page refuse more of the impact.", 0.00f, 180),
    FIRE_MARGIN(207, "The Fire Margin", Kind.WAGER,
            "Fire damage is reduced while the clause remains written.", 0.00f, 212),
    BLAST_RECEIPT(208, "The Blast Receipt", Kind.WAGER,
            "Explosion damage is reduced while the receipt remains legible.", 0.00f, 244),
    NAME_WITHOUT_RECOIL(209, "A Name Without Recoil", Kind.WAGER,
            "Knockback resistance increases. Revisions make your name harder to move.", 0.00f, 276),
    LONG_STRIDE(210, "The Long Stride", Kind.WAGER,
            "Movement speed rises at all times while the card remains active.", 0.00f, 40),
    SPRINT_CLAUSE(211, "Sprint Clause", Kind.WAGER,
            "Movement speed rises further while sprinting.", 0.00f, 60),
    EMPTY_ROAD(212, "The Empty Road", Kind.WAGER,
            "Movement speed rises when no hostile creature is close.", 0.00f, 80),
    HUNTED_ROAD(213, "The Hunted Road", Kind.WAGER,
            "Movement speed rises when at least three hostile creatures are close.", 0.00f, 99),
    OPEN_SKY_FOOTNOTE(214, "Open-Sky Footnote", Kind.WAGER,
            "Under open sky your movement quickens, and long falls are softened.", 0.00f, 117),
    DEEP_ROAD(215, "The Deep Road", Kind.WAGER,
            "Below Y 32 your movement quickens and darkness becomes easier to read.", 0.00f, 135),
    FEATHERED_DEBT(216, "Feathered Debt", Kind.WAGER,
            "Long falls repeatedly renew Slow Falling before the debt can collect.", 0.00f, 153),
    CLIMBERS_MARGIN(217, "The Climber's Margin", Kind.WAGER,
            "Jump strength is renewed while the card remains active.", 0.00f, 171),
    LONGER_HAND(218, "The Longer Hand", Kind.WAGER,
            "Block interaction reach increases modestly. Revisions extend it farther.", 0.00f, 196),
    DUELISTS_REACH(219, "The Duelist's Reach", Kind.WAGER,
            "Entity interaction reach increases modestly. Revisions lengthen the measured distance.", 0.00f, 244),
    STONE_DIVIDEND(220, "Stone Dividend", Kind.WAGER,
            "Blocks mined with a pickaxe occasionally pay a small copy of their normal drops.", 0.00f, 50),
    TIMBER_SHARE(221, "The Timber Share", Kind.WAGER,
            "Logs occasionally pay additional normal drops when broken.", 0.00f, 70),
    MASON_TITHE(222, "The Mason's Tithe", Kind.WAGER,
            "Breaking blocks sometimes repairs one point of damage on the tool that performed the work.", 0.00f, 90),
    FORTUNE_MARGIN(223, "Fortune Margin", Kind.WAGER,
            "Luck increases while the card remains active.", 0.00f, 108),
    SWIFT_TOOL(224, "The Swift Tool", Kind.WAGER,
            "Holding a digging tool renews Haste while the card remains active.", 0.00f, 126),
    HARVEST_MEMORY(225, "Harvest Memory", Kind.WAGER,
            "Mature crops occasionally repeat part of their normal harvest.", 0.00f, 144),
    GRAVE_DIVIDEND(226, "Grave Dividend", Kind.WAGER,
            "Killing hostile creatures restores a small amount of health.", 0.00f, 171),
    SCAVENGERS_NAME(227, "The Scavenger's Name", Kind.WAGER,
            "Killing hostile creatures grants a short layer of Absorption.", 0.00f, 196),
    CANDLEWORK(228, "Candlework", Kind.WAGER,
            "Low light below the surface repeatedly renews Night Vision.", 0.00f, 244),
    QUIET_LEDGER(229, "The Quiet Ledger", Kind.WAGER,
            "After ten seconds without taking damage, slow recovery begins until combat returns.", 0.00f, 308),

    QUICKENED_PULSE(230, "Quickened Pulse", Kind.WAGER,
            "Speed I answers for two minutes. Hunger I shares the first thirty seconds of the quickened blood.", 0.00f, 0),
    STONE_BREATH(231, "Stone Breath", Kind.WAGER,
            "Resistance I settles over you for ninety seconds. Slowness I shares the same stone for forty-five seconds.", 0.00f, 0),
    SHARPENED_HOUR(232, "The Sharpened Hour", Kind.WAGER,
            "Strength I lasts ninety seconds. Hunger I keeps the borrowed edge honest for thirty seconds.", 0.00f, 0),
    CLEAR_EYES(233, "Clear Eyes, Visible Name", Kind.WAGER,
            "Night Vision remains for four minutes. For the first forty-five seconds, the same clarity makes you Glow.", 0.00f, 0),
    FEATHER_RECEIPT(234, "Feather Receipt", Kind.WAGER,
            "Slow Falling remains for three minutes and Jump Boost I for ninety seconds.", 0.00f, 3),
    DIVERS_MARGIN(235, "The Diver's Margin", Kind.WAGER,
            "Water Breathing remains for four minutes. While submerged, the margin also quickens your movement.", 0.00f, 6),
    CINDER_RECEIPT(236, "Cinder Receipt", Kind.WAGER,
            "You burn for four seconds. Fire Resistance then remains for four minutes as proof of payment.", 0.00f, 8),
    MINERS_CREDIT(237, "Miner's Credit", Kind.WAGER,
            "Haste II remains for two minutes. Hunger I marks forty-five seconds of the borrowed labor.", 0.00f, 9),
    HUNTERS_REBATE(238, "Hunter's Rebate", Kind.WAGER,
            "Your next eight hostile kills each pay two experience and one hunger point.", 0.00f, 12),
    PALE_BANDAGE(239, "The Pale Bandage", Kind.WAGER,
            "Six health is restored immediately. Weakness I remains for forty-five seconds beneath the dressing.", 0.00f, 12),
    BLACK_BREAD(240, "Black Bread", Kind.WAGER,
            "Six hunger points are restored and a small Absorption layer forms. Nausea marks the meal for ten seconds.", 0.00f, 15),
    FIRST_CUT_DOUBLED(241, "The First Cut, Doubled", Kind.WAGER,
            "Your next melee hit deals fifty percent more damage. Two health is collected from you when the cut lands.", 0.00f, 15),
    FIRST_ARROW_DOUBLED(242, "The First Arrow, Doubled", Kind.WAGER,
            "Your next projectile hit deals fifty percent more damage. Slowness I follows you for five seconds when it lands.", 0.00f, 18),
    THIN_AIR(243, "Thin Air", Kind.WAGER,
            "Speed II and Jump Boost II answer together for one minute. Hunger I keeps pace with them.", 0.00f, 18),
    STANDING_ORDER(244, "Standing Order", Kind.WAGER,
            "For two minutes, remaining almost motionless repeatedly renews Resistance I.", 0.00f, 21),
    RUNNING_ORDER(245, "Running Order", Kind.WAGER,
            "For two minutes, sprinting repeatedly renews Speed I and brief Strength I.", 0.00f, 21),
    OPEN_SKY_LEDGER(246, "The Open-Sky Ledger", Kind.WAGER,
            "For three minutes, visible sky repeatedly renews Speed I and Luck I.", 0.00f, 24),
    DEEP_INK(247, "Deep Ink", Kind.WAGER,
            "For three minutes below Y 32, Haste I and Night Vision repeatedly return.", 0.00f, 24),
    RED_HARVEST(248, "The Red Harvest", Kind.WAGER,
            "Your next twelve hostile kills each return one health.", 0.00f, 27),
    GRAY_HARVEST(249, "The Gray Harvest", Kind.WAGER,
            "Your next twelve hostile kills are counted. Every third kill grants brief Absorption.", 0.00f, 27),
    SCAVENGER_CLAUSE(250, "Scavenger Clause", Kind.WAGER,
            "Your next sixteen hostile kills each have a strong chance to shake loose extra experience.", 0.00f, 30),
    MASONS_LUCK(251, "Mason's Luck", Kind.WAGER,
            "For your next sixty-four block breaks, each break has a chance to release experience.", 0.00f, 30),
    WOODSMANS_SHARE(252, "Woodsman's Share", Kind.WAGER,
            "Your next forty-eight log breaks sometimes repeat part of their normal drops.", 0.00f, 30),
    FARMERS_MARGIN(253, "Farmer's Margin", Kind.WAGER,
            "Your next thirty-two mature crop breaks sometimes repeat part of their normal harvest.", 0.00f, 30),
    IRON_STEP(254, "Iron Step", Kind.WAGER,
            "Knockback Resistance rises for three minutes. The page does not make you faster, only harder to move.", 0.00f, 34),
    GLASS_STEP(255, "Glass Step", Kind.WAGER,
            "Speed II remains for two minutes, but incoming damage is fifteen percent worse while the glass is active.", 0.00f, 34),
    WARD_LANTERN(256, "Ward Lantern", Kind.WAGER,
            "For two minutes, nearby hostile creatures are repeatedly outlined through walls.", 0.00f, 38),
    QUIET_MOUTH(257, "The Quiet Mouth", Kind.WAGER,
            "For two minutes, crouching almost motionless repeatedly renews brief Invisibility.", 0.00f, 38),
    LOUD_NAME(258, "The Loud Name", Kind.WAGER,
            "For two minutes you Glow and carry Strength I. The card makes no attempt to hide its beneficiary.", 0.00f, 38),
    SECOND_WIND(259, "Second Wind", Kind.WAGER,
            "For three minutes, the first time you fall below thirty percent health, eight health is restored and the clause closes.", 0.00f, 46),
    INKED_APPETITE(260, "Inked Appetite", Kind.WAGER,
            "Your next six completed meals each grant brief Regeneration.", 0.00f, 99),
    CHARCOAL_TONGUE(261, "Charcoal Tongue", Kind.WAGER,
            "Your next ten melee hits ignite their targets for three seconds.", 0.00f, 108),
    FROSTED_EDGE(262, "Frosted Edge", Kind.WAGER,
            "Your next ten melee hits leave Slowness I on their targets for four seconds.", 0.00f, 108),
    TETHERED_BLOOD(263, "Tethered Blood", Kind.SCAR,
            "Below half health, Resistance I answers you. Above half health, Slowness I keeps the tether taut until it is cut.", 0.00f, 126),
    CUT_TETHER(264, "Cut the Tether", Kind.REMEDY,
            "Remove Tethered Blood and end both halves of its health-bound scar.", 0.00f, 126),
    ASHEN_LUNGS(265, "Ashen Lungs", Kind.SCAR,
            "Fire Resistance remains, but Mining Fatigue I clings to every breath until the lungs are cleared.", 0.00f, 144),
    CLEAR_THE_LUNGS(266, "Clear the Lungs", Kind.REMEDY,
            "Remove Ashen Lungs. Its fireproofing and fatigue end together.", 0.00f, 144),
    HOLLOW_BONES(267, "Hollow Bones", Kind.SCAR,
            "Slow Falling repeatedly returns, but Weakness I remains until the bones are filled again.", 0.00f, 162),
    FILL_THE_BONES(268, "Fill the Bones", Kind.REMEDY,
            "Remove Hollow Bones. The borrowed lightness and weakness both end.", 0.00f, 162),
    CLOCKWORK_NERVE(269, "Clockwork Nerve", Kind.SCAR,
            "Speed I remains, but Hunger I ticks beside it until the nerve is stilled.", 0.00f, 180),
    STILL_THE_NERVE(270, "Still the Nerve", Kind.REMEDY,
            "Remove Clockwork Nerve and end both its speed and hunger.", 0.00f, 180),

    BLOODLESS_VICTORY(271, "Bloodless Victory", Kind.WAGER,
            "Your next fifteen hostile kills are judged only while you remain above eighty percent health. Each valid kill pays experience; every fifth also grants Absorption.", 0.00f, 212),
    WOUNDED_PROFIT(272, "Wounded Profit", Kind.WAGER,
            "For five minutes, outgoing damage rises by fifteen percent while you are below half health.", 0.00f, 244),
    PATIENT_BLADE(273, "The Patient Blade", Kind.WAGER,
            "For five minutes, melee damage rises by eighteen percent while your feet are nearly still.", 0.00f, 276),
    MOVING_TARGET(274, "Moving Target", Kind.WAGER,
            "For five minutes, incoming projectile damage is reduced by twenty percent while you are sprinting.", 0.00f, 276),
    CROWDED_LEDGER(275, "Crowded Ledger", Kind.WAGER,
            "For five minutes, incoming damage is reduced by fifteen percent while four or more hostiles crowd around you.", 0.00f, 308),
    SOLITARY_LEDGER(276, "Solitary Ledger", Kind.WAGER,
            "For five minutes, outgoing damage rises by fifteen percent while one or fewer hostiles stand nearby.", 0.00f, 308),
    GOLDEN_HUNGER(277, "Golden Hunger", Kind.CURSE,
            "Luck II remains. Hunger I remains with it until the appetite is paid.", 0.00f, 380),
    PAY_THE_HUNGER(278, "Pay the Hunger", Kind.REMEDY,
            "Remove Golden Hunger. Its Luck and Hunger end together.", 0.00f, 380),
    ASH_CROWN(279, "The Ash Crown", Kind.CURSE,
            "Fire Resistance remains. While wet or standing in rain, Weakness I answers the crown until it is quenched.", 0.00f, 460),
    QUENCH_THE_CROWN(280, "Quench the Crown", Kind.REMEDY,
            "Remove The Ash Crown and end both its protection and its wet-weather weakness.", 0.00f, 460),
    LAST_COAL(281, "The Last Coal", Kind.EPIC,
            "Once each Minecraft day, burning below four hearts extinguishes you, restores six health and grants brief Absorption.", 0.00f, 580),
    PALE_RESERVOIR(282, "The Pale Reservoir", Kind.EPIC,
            "Every tenth hostile kill releases a stored payment: five experience and a stronger layer of Absorption.", 0.00f, 645),
    MIRROR_LEDGER(283, "The Mirror Ledger", Kind.UNIQUE,
            "Melee attackers receive twenty percent of the damage they deal to you. Projectiles against you deal ten percent more.", 0.00f, 700),
    HUNTERS_MOON(284, "Hunter's Moon", Kind.UNIQUE,
            "Night grants Strength I and Speed I. Daylight leaves the mark visible instead.", 0.00f, 755),
    DEEP_SAINT(285, "The Deep Saint", Kind.UNIQUE,
            "Below Y 0, Resistance I and Haste I remain. Above Y 96, Slowness I answers the same law.", 0.00f, 791),
    BLACK_PARDON(286, "Black Pardon", Kind.EPIC,
            "Once each Minecraft day, carrying two or more harmful potion effects causes the page to erase all harmful effects at once.", 0.00f, 827),
    NINTH_LIFE(287, "The Ninth Life", Kind.EPIC,
            "Every forty hostile kills prepares one lethal reprieve, storing at most one. A prepared reprieve leaves you at one heart instead of dying.", 0.00f, 864),
    THE_LONG_NIGHT(288, "The Long Night", Kind.DEATH,
            "Night Vision is permanent. Night grants Strength II; direct daylight answers with Weakness I and Slowness I.", 0.00f, 944),
    GRAVE_WALKER(289, "Grave Walker", Kind.DEATH,
            "One maximum heart is buried. Ordinary undead repeatedly forget you as a combat target.", 0.00f, 1011),


    SWIFT_MERCY(290, "Swift Mercy", Kind.WAGER,
            "Speed I carries you for ninety seconds; a brief pulse of Regeneration opens the clause.", 0.00f, 0),
    IRON_WAKE(291, "Iron Wake", Kind.WAGER,
            "Resistance I lasts seventy-five seconds. Strength I accompanies the first forty-five.", 0.00f, 0),
    MOONWATER_DRAFT(292, "Moonwater Draft", Kind.WAGER,
            "Night Vision and Water Breathing remain together for three minutes.", 0.00f, 6),
    FURNACE_VEIN(293, "Furnace Vein", Kind.WAGER,
            "Fire Resistance and Haste I answer for two minutes.", 0.00f, 6),
    QUIET_STEP(294, "Quiet Step", Kind.WAGER,
            "For two minutes, crouching repeatedly renews Resistance I; the benefit ends when you stand.", 0.00f, 9),
    RED_HOUR(295, "The Red Hour", Kind.WAGER,
            "Strength II lasts one minute. Hunger II keeps the same hour honestly expensive.", 0.00f, 12),
    PALE_STEP(296, "Pale Step", Kind.WAGER,
            "Slow Falling and Jump Boost II remain for two and a half minutes.", 0.00f, 12),
    HUNGRY_STEEL(297, "Hungry Steel", Kind.WAGER,
            "Strength I remains for two minutes, with Hunger I bound to the same term.", 0.00f, 15),
    WATCHERS_DRAFT(298, "Watcher's Draft", Kind.WAGER,
            "Luck II remains for three minutes. The first minute also writes your outline in Glowing ink.", 0.00f, 15),
    STONE_CHOIR(299, "Stone Choir", Kind.WAGER,
            "For three minutes, standing nearly still repeatedly renews Haste II.", 0.00f, 18),
    RUNNING_DEBT(300, "Running Debt", Kind.WAGER,
            "For three minutes, sprinting renews Speed II and Hunger I together.", 0.00f, 18),
    BLACK_CURRENT(301, "Black Current", Kind.WAGER,
            "For three minutes, water grants Water Breathing and Dolphin's Grace.", 0.00f, 21),
    HOLLOW_LIGHT(302, "Hollow Light", Kind.WAGER,
            "Night Vision remains for three minutes while nearby hostiles are repeatedly outlined.", 0.00f, 21),
    LAST_MATCH(303, "The Last Match", Kind.WAGER,
            "Fire Resistance remains for three minutes; Strength I burns brightest through the first thirty seconds.", 0.00f, 24),
    GRAVE_SALT(304, "Grave Salt", Kind.WAGER,
            "Your next twelve undead kills each pay three experience.", 0.00f, 27),
    ASH_DIVIDEND(305, "Ash Dividend", Kind.WAGER,
            "Your next twelve hostile kills each pay one experience; every third also grants brief Regeneration.", 0.00f, 27),
    FIRST_BLOOD(306, "First Blood", Kind.WAGER,
            "Your next melee hit deals sixty percent more damage and returns two health when it lands.", 0.00f, 30),
    LAST_ARROW(307, "The Last Arrow", Kind.WAGER,
            "Your next projectile hit deals sixty percent more damage. Weakness I follows you for five seconds.", 0.00f, 30),
    LONG_BREATH(308, "Long Breath", Kind.WAGER,
            "Water Breathing remains for five minutes; while submerged, brief Regeneration is repeatedly renewed.", 0.00f, 34),
    DEEP_STEP(309, "Deep Step", Kind.WAGER,
            "For four minutes below Y 0, Haste II repeatedly returns.", 0.00f, 38),
    SKY_STEP(310, "Sky Step", Kind.WAGER,
            "For four minutes above Y 96, Speed I and Jump Boost I repeatedly return.", 0.00f, 38),
    HUNTERS_REST(311, "Hunter's Rest", Kind.WAGER,
            "For four minutes, remaining nearly still below half health repeatedly renews Regeneration I.", 0.00f, 46),
    HUNTERS_RUSH(312, "Hunter's Rush", Kind.WAGER,
            "For four minutes, sprinting repeatedly renews Strength I.", 0.00f, 46),
    RED_LEDGER(313, "The Red Ledger", Kind.WAGER,
            "Your next sixteen hostile kills are counted; every fourth heals two health and pays extra experience.", 0.00f, 54),
    BLACK_LEDGER(314, "The Black Ledger", Kind.WAGER,
            "Your next sixteen hostile kills each pay three experience but collect one food point.", 0.00f, 54),
    CANDLE_TAX(315, "Candle Tax", Kind.WAGER,
            "Your next eight completed meals grant brief Absorption and five seconds of Nausea.", 0.00f, 62),
    IRON_HARVEST(316, "Iron Harvest", Kind.WAGER,
            "Your next forty-eight ore breaks each release one or two experience.", 0.00f, 70),
    STONE_HARVEST(317, "Stone Harvest", Kind.WAGER,
            "Your next sixty-four pickaxe-worthy block breaks sometimes release experience.", 0.00f, 70),
    GLASS_RUNNER(318, "Glass Runner", Kind.WAGER,
            "Speed I and Jump Boost II remain for two and a half minutes, but incoming damage rises by ten percent.", 0.00f, 80),
    HEAVY_HAND(319, "Heavy Hand", Kind.WAGER,
            "Attack knockback rises for three minutes.", 0.00f, 90),
    THIN_ARMOR(320, "Thin Armor", Kind.WAGER,
            "Speed I remains for three minutes, but four points of armor are written out for the same duration.", 0.00f, 99),
    CORPSE_LANTERN(321, "Corpse Lantern", Kind.WAGER,
            "For three minutes, nearby undead are repeatedly outlined through walls.", 0.00f, 108),
    WOLFS_DEBT(322, "Wolf's Debt", Kind.WAGER,
            "For four minutes, three or more nearby hostiles repeatedly renew Strength I.", 0.00f, 126),
    EMPTY_ROOM(323, "The Empty Room", Kind.WAGER,
            "For four minutes, having no nearby hostiles repeatedly renews Resistance I and Luck I.", 0.00f, 135),
    CROWDED_ROOM(324, "The Crowded Room", Kind.WAGER,
            "For four minutes, four or more nearby hostiles repeatedly renew Resistance I.", 0.00f, 144),
    RAIN_CLERK(325, "The Rain Clerk", Kind.WAGER,
            "For four minutes, rain or water repeatedly renews Speed I.", 0.00f, 162),
    SUN_CLERK(326, "The Sun Clerk", Kind.WAGER,
            "For four minutes, direct daylight repeatedly renews Haste I.", 0.00f, 180),
    MOON_CLERK(327, "The Moon Clerk", Kind.WAGER,
            "For four minutes, night repeatedly renews Speed I.", 0.00f, 212),
    BLOOD_CLOCK(328, "Blood Clock", Kind.WAGER,
            "For four minutes below half health, Speed I and Strength I repeatedly return.", 0.00f, 244),
    CLEAN_HANDS(329, "Clean Hands", Kind.WAGER,
            "For four minutes above eighty percent health, Resistance I repeatedly returns.", 0.00f, 276),
    COLD_IRON(330, "Cold Iron", Kind.SCAR,
            "Knockback Resistance remains, but Slowness I clings to every step until the iron is warmed.", 0.00f, 324),
    WARM_IRON(331, "Warm the Iron", Kind.REMEDY,
            "Remove Cold Iron and end both its weight and resistance.", 0.00f, 324),
    PALE_SKIN(332, "Pale Skin", Kind.SCAR,
            "Night Vision remains. Direct daylight repeatedly writes Weakness I across the skin until color is returned.", 0.00f, 400),
    COLOR_RETURNED(333, "Color Returned", Kind.REMEDY,
            "Remove Pale Skin and end its night sight and daylight weakness.", 0.00f, 400),
    SALT_LUNGS(334, "Salt Lungs", Kind.SCAR,
            "Water Breathing remains. On dry land, Hunger I follows every breath until fresh air is restored.", 0.00f, 480),
    FRESH_AIR(335, "Fresh Air", Kind.REMEDY,
            "Remove Salt Lungs and end both its water gift and dry-land hunger.", 0.00f, 480),
    EMBER_MORTGAGE(336, "Ember Mortgage", Kind.CURSE,
            "Fire Resistance remains, but Glowing remains with it until the ember is paid.", 0.00f, 580),
    PAY_THE_EMBER(337, "Pay the Ember", Kind.REMEDY,
            "Remove Ember Mortgage and end its fireproofing and visible mark together.", 0.00f, 580),
    GRAVE_CREDIT(338, "Grave Credit", Kind.EPIC,
            "Every twentieth hostile kill releases stored credit: four health, eight experience and brief Resistance II.", 0.00f, 664),
    EMPTY_PULSE(339, "The Empty Pulse", Kind.DEATH,
            "One maximum heart is buried. Below four hearts, Speed II and Strength I repeatedly answer the missing pulse.", 0.00f, 809),

    KNEELING_FUSE(340, "The Kneeling Fuse", Kind.CURSE,
            "The first instant of every crouch lights a TNT-strength charge at your feet. Holding the crouch does not repeat it; kneeling again does.", 0.00f, 460),
    DROWN_THE_FUSE(341, "Drown the Fuse", Kind.REMEDY,
            "Erase The Kneeling Fuse before another bow becomes an explosion.", 0.00f, 460),
    THIRTEENTH_STEP(342, "The Thirteenth Step", Kind.CURSE,
            "Distance is counted in secret. Every thirteenth block walked calls a brief lightning judgment onto your position.", 0.00f, 540),
    BREAK_THE_COUNT(343, "Break the Count", Kind.REMEDY,
            "Erase The Thirteenth Step and stop the hidden count.", 0.00f, 540),
    STILLNESS_TAX(344, "Stillness Tax", Kind.CURSE,
            "Remain nearly still for six seconds and the page violently rejects the pause, shoving you away and writing Darkness across your sight.", 0.00f, 609),
    MOVE_THE_INK(345, "Move the Ink", Kind.REMEDY,
            "Erase Stillness Tax and permit the body to remain where it is.", 0.00f, 609),
    BLACK_STATIC(346, "Black Static", Kind.CURSE,
            "At irregular intervals the nine hotbar positions rotate without asking which hand was meant to hold what.", 0.00f, 645),
    SORT_THE_HAND(347, "Sort the Hand", Kind.REMEDY,
            "Erase Black Static. The hotbar stops rearranging itself.", 0.00f, 645),
    CHORUS_DEBT(348, "Chorus Debt", Kind.CURSE,
            "Heavy wounds sometimes displace you several blocks sideways before you can decide whether that was rescue or sabotage.", 0.00f, 682),
    CLOSE_THE_CHORUS(349, "Close the Chorus", Kind.REMEDY,
            "Erase Chorus Debt and make wounds happen where they were received.", 0.00f, 682),

    LAST_FOOTPRINT(350, "The Last Footprint", Kind.EPIC,
            "Once every ninety seconds, a wound taken below thirty-five percent health returns you to where you stood roughly four seconds earlier and restores a little blood.", 0.00f, 718),
    SECOND_GRAVITY(351, "Second Gravity", Kind.EPIC,
            "Once every thirty seconds, a fall longer than eight blocks becomes a landing sentence: the fall is erased and nearby hostiles take the impact instead.", 0.00f, 736),
    WITNESS_MARK(352, "Witness Mark", Kind.UNIQUE,
            "The first hostile to wound you is entered as witness for twenty seconds. Your damage against that witness rises; attacks against others are slightly reduced until the record closes.", 0.00f, 755),
    BLACKOUT_CLAUSE(353, "Blackout Clause", Kind.UNIQUE,
            "In near-total darkness your attacks become heavier and hostile kills briefly erase your outline. Bright light makes your strikes slightly less certain.", 0.00f, 755),
    BONE_MAGNET(354, "Bone Magnet", Kind.UNIQUE,
            "Badly wounded hostiles within eight blocks are slowly drawn toward you, as though their last few steps have already been notarized.", 0.00f, 773),
    BLOOD_CLOCK_HAND(355, "Blood Clock Hand", Kind.EPIC,
            "Your attack speed rises as your health falls, reaching its fastest cadence near death.", 0.00f, 791),
    STOLEN_COUNTENANCE(356, "Stolen Countenance", Kind.EPIC,
            "Killing a creature carrying a beneficial status copies one of those effects onto you for at most thirty seconds.", 0.00f, 809),
    HOUSE_ALWAYS_WINS(357, "The House Always Wins", Kind.UNIQUE,
            "Hostile kills are counted in eights. The seventh pays a jackpot; the eighth immediately collects food and strips your Absorption before the count begins again.", 0.00f, 809),
    RETURN_TO_SENDER(358, "Return to Sender", Kind.UNIQUE,
            "Once every thirty seconds, an incoming projectile deals half damage and folds you to the shooter's flank if a safe place exists there.", 0.00f, 827),
    ASH_RECOIL(359, "Ash Recoil", Kind.EPIC,
            "Explosion damage against you is reduced, but every blast throws you upward and leaves Slow Falling behind the recoil.", 0.00f, 827),

    AIRBORNE_LEDGER(360, "Airborne Ledger", Kind.WAGER,
            "For four minutes, hostile kills made while airborne store up to three landing charges. Touching ground spends them in a damaging shockwave.", 0.00f, 755),
    QUIET_EXECUTION(361, "Quiet Execution", Kind.WAGER,
            "For four minutes, killing a hostile while crouched grants six seconds of Invisibility and a short burst of Speed II.", 0.00f, 773),
    RED_PURSUIT(362, "Red Pursuit", Kind.WAGER,
            "For four minutes, damaging a hostile marks it for five seconds. Killing the marked target in time restores four health and accelerates you briefly.", 0.00f, 791),
    CROW_TOLL(363, "Crow Toll", Kind.EPIC,
            "Every twelfth hostile kill rings once: nearby loose projectiles are erased and a layer of Absorption is written around you.", 0.00f, 845),
    PROJECTILE_AMNESTY(364, "Projectile Amnesty", Kind.EPIC,
            "Every fifth projectile that would wound you is dismissed entirely and converted into a small experience payment.", 0.00f, 864),
    LOANED_MOMENT(365, "Loaned Moment", Kind.UNIQUE,
            "Once every twenty seconds, taking a heavy wound slows nearby hostiles almost to a stop while lending you their missing seconds as Speed II.", 0.00f, 882),
    DEAD_MANS_MARGIN(366, "Dead Man's Margin", Kind.UNIQUE,
            "Below one quarter health, melee strikes hit much harder and shove their target away with the violence of a closing margin.", 0.00f, 900),
    CROOKED_PARALLAX(367, "Crooked Parallax", Kind.EPIC,
            "Incoming projectiles lose much of their force while you are moving sharply sideways relative to where you are looking.", 0.00f, 922),
    BELL_WITHOUT_SOUND(368, "Bell Without Sound", Kind.UNIQUE,
            "Every thirty seconds the nearest hostile within twenty blocks is silently marked with Glowing and Weakness. Killing that marked witness pays experience.", 0.00f, 944),
    GRAVE_INTEREST(369, "Grave Interest", Kind.EPIC,
            "At level thirty or higher, incoming damage is discounted by thirty percent, but every wound burns two experience points as interest.", 0.00f, 967),

    BLOOD_TELEGRAM(370, "Blood Telegram", Kind.UNIQUE,
            "Melee damage sends a quarter-strength copy through up to four nearby hostiles of the exact same creature type.", 0.00f, 900),
    WOUND_EXCHANGE(371, "Wound Exchange", Kind.EPIC,
            "Once every forty-five seconds while below one quarter health, your next melee strike takes six additional health from the target and returns six to you.", 0.00f, 922),
    STILL_POINT_BLACK(372, "Still Point in Black", Kind.WAGER,
            "For four minutes, standing almost perfectly still for three seconds charges the next projectile you land to deal double damage.", 0.00f, 900),
    BLIND_AUCTION(373, "Blind Auction", Kind.CURSE,
            "Every seventy-five seconds the auction closes your eyes for eight seconds. Strength II is granted for exactly the same blind interval.", 0.00f, 944),
    CANCEL_AUCTION(374, "Cancel the Auction", Kind.REMEDY,
            "Erase Blind Auction and stop bidding with your eyesight.", 0.00f, 944),
    LAST_WARDEN(375, "The Last Warden", Kind.EPIC,
            "When five or more hostiles crowd you, a twenty-second internal bell eventually releases a knockback pulse and grants brief Absorption.", 0.00f, 989),
    FURNACE_HEART(376, "Furnace Heart", Kind.DEATH,
            "One maximum heart is buried. Fire and lava continually mend you and Fire Resistance remains; water repeatedly damages the furnace instead.", 0.00f, 1011),
    KING_IN_RAGS(377, "King in Rags", Kind.UNIQUE,
            "With six or fewer armor points you gain Strength II and Speed I. Heavy armor reverses the crown into Weakness.", 0.00f, 989),
    IRON_IDOL(378, "Iron Idol", Kind.UNIQUE,
            "At eighteen or more armor points you gain Resistance I, but the same weight continuously applies Slowness I.", 0.00f, 1011),
    LAST_CARD_DRAWN(379, "The Last Card Drawn", Kind.EPIC,
            "Once every ninety seconds below three hearts, the deck chooses one of five emergency clauses for you. Every clause saves differently and invoices a different drawback.", 0.00f, 1056),

    NULL_SPRINT(380, "Null Sprint", Kind.DEATH,
            "One maximum heart is buried. Sprinting sheds a short damaging trail into nearby hostiles, but continuously exhausts the body that writes it.", 0.00f, 1078),
    WORLD_OWES_NOTHING(381, "The World Owes Nothing", Kind.DEATH,
            "One maximum heart is buried. Some hostile kills yield no drops at all; every tenth successful collection instead duplicates the corpse's ordinary drops.", 0.00f, 1116),
    RED_ECHO(382, "Red Echo", Kind.DEATH,
            "One maximum heart is buried. Melee wounds against you arrive heavier, but part of every such wound is immediately echoed back into the attacker.", 0.00f, 1148),
    PALE_RECOIL(383, "Pale Recoil", Kind.DEATH,
            "One maximum heart is buried. Your projectiles strike far harder, but each successful projectile hit takes a small recoil payment directly from you.", 0.00f, 1148),
    FIFTH_TOLL(384, "The Fifth Toll", Kind.EPIC,
            "Every fifth hostile kill calls a visible lightning judgment onto the corpse and releases a short damaging ring through nearby enemies. You are not fully exempt from the toll.", 0.00f, 1180),
    HOLLOW_CROWN(385, "Hollow Crown", Kind.DEATH,
            "Two maximum hearts are buried. Wearing no armor grants Strength II, Speed II and Resistance I; wearing any armor answers with Weakness II and Slowness I.", 0.00f, 1212),
    AFTERIMAGE_DEBT(386, "Afterimage Debt", Kind.UNIQUE,
            "Sprint far enough to prepare an afterimage. Your next melee strike is repeated a moment later for forty percent of the original damage.", 0.00f, 1116),
    DEBT_OF_DISTANCE(387, "Debt of Distance", Kind.UNIQUE,
            "Damage dealt from far away grows with distance up to a severe premium. Fighting almost point-blank instead loses part of its force.", 0.00f, 1148),
    FINAL_AUCTION(388, "The Final Auction", Kind.EPIC,
            "Every fortieth hostile kill auctions one pair of temporary advantages and liabilities at random. The House chooses the lot; you receive both sides.", 0.00f, 1212),
    UNWRITTEN_REMAINDER(389, "The Unwritten Remainder", Kind.DEATH,
            "Two maximum hearts are buried. Once every ten minutes, lethal damage leaves one health instead; the remainder erases one ordinary hotbar stack, or ten experience levels if nothing disposable is present.", 0.00f, 1276),

    THE_RED_PEN(390, "The Red Pen", Kind.WAGER,
            "Do not sign a law. Strike one other card from the hand in front of you, then choose again from what remains. The global catalogue is untouched.", 0.00f, 70),
    DEBT_ECHO(391, "Double Entry", Kind.DEBT,
            "Arm the Debt echo. The next Debt card you actually sign resolves as a doubled entry.", 0.00f, 126),
    WAGER_ECHO(392, "Second Stake", Kind.WAGER,
            "Arm the Wager echo. The next Wager card you actually sign resolves one grade stronger.", 0.00f, 135),
    SCAR_ECHO(393, "Twin Scar", Kind.SCAR,
            "Arm the Scar echo. The next Scar card you actually sign is written twice into the same mark.", 0.00f, 162),
    REMEDY_ECHO(394, "Second Remedy", Kind.REMEDY,
            "Arm the Remedy echo. The next Remedy you sign resolves with doubled force where its terms can scale.", 0.00f, 171),
    CONTRACT_ECHO(395, "Carbon Contract", Kind.CONTRACT,
            "Arm the Contract echo. The next Contract you sign receives a stronger resolution without creating a second objective.", 0.00f, 276),
    RITUAL_ECHO(396, "Resonant Rite", Kind.RITUAL,
            "Arm the Ritual echo. The next Ritual you sign resonates as one strengthened rite, never as a duplicate objective.", 0.00f, 340),
    COVENANT_ECHO(397, "Covenant in Duplicate", Kind.COVENANT,
            "Arm the Covenant echo. The next Covenant you sign resolves a stronger clause without opening a second obligation slot.", 0.00f, 460),
    MASTER_ECHO(398, "The Dealer's Echo", Kind.MASTER,
            "Arm the Master echo. The next private Master law you sign is copied into a stronger single signature.", 0.00f, 609),
    EPIC_ECHO(399, "Gold Resonance", Kind.EPIC,
            "Arm the Epic echo. The next Epic card you sign resolves one grade stronger.", 0.00f, 645),
    UNIQUE_ECHO(400, "Impossible Duplicate", Kind.UNIQUE,
            "Arm the Unique echo. The next Unique card you sign carries a doubled resonance.", 0.00f, 718),
    CURSE_ECHO(401, "Black Echo", Kind.CURSE,
            "Arm the Curse echo. The next Curse you are forced to sign is intensified instead of creating a second curse object.", 0.00f, 791),
    DEATH_ECHO(402, "Grave Echo", Kind.DEATH,
            "Arm the Death echo. The next Death law you sign resonates twice while remaining one law in the ledger.", 0.00f, 989),

    MIRROR_WRIT(403, "Mirror Writ", Kind.EPIC,
            "Face an incoming projectile and the first clean interception after the writ cools is returned to its owner instead of paid into you.", 0.00f, 864),
    BORROWED_ANATOMY(404, "Borrowed Anatomy", Kind.UNIQUE,
            "The last hostile creature you kill lends you one intrinsic trick until another hostile replaces the specimen.", 0.00f, 922),
    NINTH_MARGIN(405, "The Ninth Margin", Kind.EPIC,
            "An empty ninth hotbar slot becomes a virtual spell margin. Select it and press the Wardbound spell key to cast the ability remembered from your last hostile kill.", 0.00f, 967),
    CORPSE_LEDGER(406, "The Corpse Ledger", Kind.DEATH,
            "One maximum heart is buried. Death records a corpse debt at the place you fell. Return to that place to close it; dying again before payment deepens the debt.", 0.00f, 1056),
    CHAIN_OF_CUSTODY(407, "Chain of Custody", Kind.EPIC,
            "While crouching, breaking an ore or log also breaks a short connected chain of the same block, paying tool durability and hunger for every extra link.", 0.00f, 827),

    CLEAN_INTEREST(408, "Clean Interest", Kind.WAGER,
            "Clean and Perfect ward resolutions leave a brief useful afterglow; perfection also quickens the feet.", 0.00f, 627),
    SECOND_ATTEMPT(409, "The Second Attempt", Kind.EPIC,
            "Failing an ordinary ward writes one clue and four extra seconds into the next ordinary ward instead of making the failure easier retroactively.", 0.00f, 664),
    THREE_CLEAN_LINES(410, "Three Clean Lines", Kind.UNIQUE,
            "Every third consecutive Clean or Perfect resolution banks one Mercy, up to two. The next ordinary ward spends one Mercy to forgive its first significant error.", 0.00f, 755),
    HURRIED_OATH(411, "The Hurried Oath", Kind.WAGER,
            "Your next three ordinary wards run slightly faster and pay +20% loot.", 0.00f, 645),
    WHITE_INK(412, "White Ink", Kind.WAGER,
            "Your next two ordinary wards expose one reliable clue, but each pays 8% less loot.", 0.00f, 664),
    BOUND_TESTIMONY(413, "Bound Testimony", Kind.WAGER,
            "Your next two ordinary wards bind one moving element and pay +28% loot for accepting the constraint.", 0.00f, 682),
    DOUBLE_MARGIN(414, "Double Margin", Kind.EPIC,
            "Your next two ordinary wards gain six seconds and +20% loot, but begin with one fewer life.", 0.00f, 718),
    PERFECT_ORRERY(415, "The Perfect Orrery", Kind.EPIC,
            "A Perfect Black Orrery resolution leaves forty-five seconds of Resistance and a shorter burst of Haste II.", 0.00f, 791),
    PERFECT_PROCESSION(416, "The Perfect Procession", Kind.EPIC,
            "A Perfect Last Procession resolution leaves Luck II and Speed I as proof that every witness stood in the only possible place.", 0.00f, 791),
    BLACK_STUDY(417, "The Black Study", Kind.CURSE,
            "Failing a logic discipline writes Darkness and Weakness across the next few breaths until the Study is closed.", 0.00f, 700),
    CLOSE_THE_BOOK(418, "Close the Book", Kind.REMEDY,
            "Erase The Black Study and its failure sentence.", 0.00f, 700),
    ASHEN_REBUTTAL(419, "Ashen Rebuttal", Kind.WAGER,
            "A heavy wound prepares one rebuttal for four seconds. Your next direct melee strike spends it for six additional damage; the page then cools.", 0.00f, 664),
    SEVEN_PACES(420, "Seven Paces", Kind.WAGER,
            "Sprinting without interruption for seven seconds prepares a violent next melee strike. The road must cool before it can count again.", 0.00f, 700),
    STILL_WITNESS(421, "The Still Witness", Kind.EPIC,
            "Remain almost perfectly still on solid ground for two and a half seconds to receive brief Absorption. The witness cannot testify continuously.", 0.00f, 755),
    HOLLOW_STEP(422, "The Hollow Step", Kind.UNIQUE,
            "Crouch and remain still for two seconds to disappear briefly. Attacking tears the veil immediately, and the step needs time before it returns.", 0.00f, 827),
    RED_WAKE(423, "The Red Wake", Kind.EPIC,
            "Hostile kills accelerate you. Every third kill in the wake also writes a brief Strength clause.", 0.00f, 755),
    IRON_AFTERTASTE(424, "Iron Aftertaste", Kind.WAGER,
            "Breaking ore prepares the next direct melee strike for ten seconds, adding four damage before the iron taste fades.", 0.00f, 682),
    TIMBER_ECHO(425, "Timber Echo", Kind.WAGER,
            "Breaking a natural log echoes through the limbs as six seconds of Speed and Haste.", 0.00f, 682),
    RAIN_WRIT(426, "The Rain Writ", Kind.EPIC,
            "Under open rain, Speed repeatedly returns and projectile damage rises by fifteen percent.", 0.00f, 791),
    CENSUS_OF_ONE(427, "Census of One", Kind.UNIQUE,
            "Exactly one nearby hostile grants Strength; four or more grant Resistance. The law rewards reading the crowd rather than merely enlarging it.", 0.00f, 845),
    EMPTY_HAND_DOCTRINE(428, "Empty-Hand Doctrine", Kind.WAGER,
            "With an empty offhand, direct melee damage rises by ten percent.", 0.00f, 718),
    FULL_HAND_DOCTRINE(429, "Full-Hand Doctrine", Kind.WAGER,
            "With anything in the offhand, brief Resistance repeatedly renews. The doctrine chooses readiness over reach.", 0.00f, 718),
    WOUND_CLOCK(430, "The Wound Clock", Kind.CURSE,
            "Meaningful wounds slow you for three seconds. Another wound before the clock settles deepens the Slowness.", 0.00f, 755),
    STOP_THE_CLOCK(431, "Stop the Clock", Kind.REMEDY,
            "Erase The Wound Clock and its accumulated cadence.", 0.00f, 755),
    HUNGER_OF_ORDER(432, "Hunger of Order", Kind.CURSE,
            "Combat repeatedly lends Strength I, but the same ordered violence consumes extra exhaustion while the fight remains active.", 0.00f, 791),
    BREAK_THE_ORDER(433, "Break the Order", Kind.REMEDY,
            "Erase Hunger of Order and end both its Strength and its appetite.", 0.00f, 791),
    GRAVE_RECEIPT(434, "The Grave Receipt", Kind.DEATH,
            "Each death signs three receipts. Your next three hostile kills after that death each return one heart and brief Regeneration II.", 0.00f, 900),
    FINAL_FOOTNOTE(435, "The Final Footnote", Kind.DEATH,
            "Killing a hostile while at two hearts or less can grant ten seconds of Resistance II and Absorption II. The footnote requires a full minute before it can be cited again.", 0.00f, 944),
    MOON_ARCHIVE(436, "The Moon Archive", Kind.UNIQUE,
            "Hostile kills at night store up to five pages. A meaningful incoming wound consumes one page to reduce that wound by twenty percent and return a little blood.", 0.00f, 864),
    SIXTH_WITNESS(437, "The Sixth Witness", Kind.UNIQUE,
            "Perfect ward resolutions store testimony, up to three. With three testimonies recorded, your next hostile kill releases an eight-block soul verdict against nearby hostiles.", 0.00f, 989),

    // Apothic Attributes wave: timed build experiments, paired attribute scars/remedies,
    // persistent late-game laws, and Death-tier attribute rewrites.
    RAZOR_DIVIDEND(438, "Razor Dividend", Kind.WAGER,
            "For ninety seconds gain +4 armor piercing and +8% Apothic critical chance.", 0.00f, 460),
    SPLINTERED_PLATE(439, "Splintered Plate", Kind.WAGER,
            "For ninety seconds shred 15% armor and deal bonus physical damage equal to 2% of the target's current health.", 0.00f, 500),
    LONGBOW_TESTAMENT(440, "Longbow Testament", Kind.WAGER,
            "For two minutes arrows gain +28% damage and +18% velocity; the velocity increase also feeds their impact.", 0.00f, 540),
    QUICKDRAW_CLAUSE(441, "Quickdraw Clause", Kind.WAGER,
            "For two minutes ranged weapons draw 35% faster and projectiles leave the string 12% faster.", 0.00f, 540),
    CRIMSON_ODDS(442, "Crimson Odds", Kind.WAGER,
            "For ninety seconds gain +12% critical chance and +30% critical damage, but all healing received is reduced by 10%.", 0.00f, 580),
    FROST_WIT(443, "Frost Wit", Kind.WAGER,
            "For two minutes attacks carry +2.5 cold magic damage and you gain +5% dodge chance.", 0.00f, 609),
    CINDER_WIT(444, "Cinder Wit", Kind.WAGER,
            "For two minutes attacks carry +2.5 fire magic damage and critical strikes gain another +20% damage multiplier.", 0.00f, 609),
    BLOOD_RETURN(445, "Blood Return", Kind.WAGER,
            "For ninety seconds convert 6% of physical damage into health and receive 10% more healing.", 0.00f, 627),
    PALE_RESERVE(446, "Pale Reserve", Kind.WAGER,
            "For two minutes 8% of physical damage can become absorption through overheal and healing received rises by 12%.", 0.00f, 627),
    DEEP_MINERS_LEDGER(447, "The Deep Miner's Ledger", Kind.WAGER,
            "For three minutes mining speed rises by 40% and experience gained rises by 15%.", 0.00f, 645),
    SCHOLARS_TITHE(448, "Scholar's Tithe", Kind.WAGER,
            "For two minutes experience gained rises by 55%, but all healing received falls by 10%.", 0.00f, 645),
    DODGERS_INK(449, "Dodger's Ink", Kind.WAGER,
            "For two minutes gain +12% dodge chance, but ranged weapons draw 10% slower.", 0.00f, 664),
    PLATEBREAKER_SCRIPT(450, "Platebreaker Script", Kind.WAGER,
            "For two minutes attacks ignore 3 armor points and 2 enchantment-protection points.", 0.00f, 682),
    SUNDERERS_NOTE(451, "Sunderer's Note", Kind.WAGER,
            "For two minutes attacks shred 10% armor and 18% enchantment protection.", 0.00f, 700),
    OPENING_FEE(452, "Opening Fee", Kind.WAGER,
            "For ninety seconds deal bonus physical damage equal to 4% of a target's current health and gain +5% critical chance.", 0.00f, 718),
    HUNTERS_SCRIPT(453, "Hunter's Script", Kind.WAGER,
            "For two minutes arrows deal +20% damage and ranged weapons draw 25% faster.", 0.00f, 718),
    BLOODLETTERS_MARGIN(454, "Bloodletter's Margin", Kind.WAGER,
            "For two minutes gain 4% life steal and +8% critical chance.", 0.00f, 736),
    WARMTH_AGAINST_STEEL(455, "Warmth Against Steel", Kind.WAGER,
            "For two minutes attacks carry +3 fire magic damage and ignore 2 armor points.", 0.00f, 755),
    WINTER_AGAINST_BONE(456, "Winter Against Bone", Kind.WAGER,
            "For two minutes attacks carry +3 cold magic damage and deal 2% of current health as bonus physical damage.", 0.00f, 755),
    SWIFT_PICK_CLAUSE(457, "Swift Pick Clause", Kind.WAGER,
            "For two minutes mining speed rises by 50% and experience gained rises by 20%.", 0.00f, 773),
    SILVER_LEDGER(458, "The Silver Ledger", Kind.WAGER,
            "For three minutes experience gained rises by 35% and dodge chance rises by 6%.", 0.00f, 791),
    SECOND_HEART_ACCOUNTING(459, "Second-Heart Accounting", Kind.WAGER,
            "For two minutes gain 10% overheal and 2.5% life steal.", 0.00f, 809),
    DOUBLED_EDGE(460, "The Doubled Edge", Kind.WAGER,
            "For ninety seconds critical damage rises by 50%, but healing received falls by 15%.", 0.00f, 827),
    IMPOSSIBLE_AIM(461, "Impossible Aim", Kind.WAGER,
            "For two minutes arrows deal +35% damage and critical chance rises by 8%, but ranged draw speed falls by 15%.", 0.00f, 845),

    RUSTED_EDGE(462, "Rusted Edge", Kind.CURSE,
            "Permanently shred 12% armor, but receive 20% less healing until the rust is cleansed.", 0.00f, 664),
    CLEAN_THE_RUST(463, "Clean the Rust", Kind.REMEDY,
            "Remove Rusted Edge and both sides of its attribute bargain.", 0.00f, 664),
    SPLIT_NERVE(464, "Split Nerve", Kind.SCAR,
            "Permanently gain +10% critical chance, but ranged draw speed falls by 25% until the nerve is mended.", 0.00f, 700),
    MEND_THE_NERVE(465, "Mend the Nerve", Kind.REMEDY,
            "Remove Split Nerve and restore the normal critical/draw-speed balance.", 0.00f, 700),
    HOLLOW_MARROW(466, "Hollow Marrow", Kind.CURSE,
            "Permanently gain 8% life steal, but receive 30% less healing until the marrow is filled.", 0.00f, 736),
    FILL_THE_MARROW(467, "Fill the Marrow", Kind.REMEDY,
            "Remove Hollow Marrow and its life-steal/healing exchange.", 0.00f, 736),
    BROKEN_SIGHT(468, "Broken Sight", Kind.SCAR,
            "Permanently gain +35% arrow damage, but ranged draw speed falls by 30% until the sight is reset.", 0.00f, 773),
    RESET_THE_SIGHT(469, "Reset the Sight", Kind.REMEDY,
            "Remove Broken Sight and restore the normal ranged balance.", 0.00f, 773),
    ASHEN_PICK(470, "Ashen Pick", Kind.CURSE,
            "Permanently gain +50% mining speed, but experience gained falls by 30% until the pick is washed clean.", 0.00f, 809),
    WASH_THE_PICK(471, "Wash the Pick", Kind.REMEDY,
            "Remove Ashen Pick and its mining/experience exchange.", 0.00f, 809),
    OPEN_WOUND_LEDGER(472, "The Open Wound Ledger", Kind.CURSE,
            "Permanently deal 5% of current health as bonus physical damage, but receive 25% less healing until the wound is closed.", 0.00f, 845),
    CLOSE_THE_WOUND(473, "Close the Wound", Kind.REMEDY,
            "Remove The Open Wound Ledger and its current-health damage clause.", 0.00f, 845),

    RAZOR_DOCTRINE(474, "The Razor Doctrine", Kind.EPIC,
            "A persistent law grants +5 armor piercing and +8% critical chance.", 0.00f, 900),
    SUNDERED_CREED(475, "The Sundered Creed", Kind.UNIQUE,
            "A persistent law shreds 18% armor and 12% enchantment protection.", 0.00f, 944),
    GOLDEN_BALLISTICS(476, "Golden Ballistics", Kind.EPIC,
            "A persistent law grants +30% arrow damage, +25% draw speed and +12% arrow velocity.", 0.00f, 944),
    SECOND_STRING(477, "The Second String", Kind.UNIQUE,
            "A persistent law grants +10% critical chance and +35% critical damage.", 0.00f, 989),
    WINTER_TONGUE(478, "Winter Tongue", Kind.EPIC,
            "A persistent law adds 3 cold magic damage to attacks and +6% dodge chance.", 0.00f, 989),
    CINDER_TONGUE(479, "Cinder Tongue", Kind.EPIC,
            "A persistent law adds 3 fire magic damage to attacks and +8% critical chance.", 0.00f, 989),
    RED_CATECHISM(480, "The Red Catechism", Kind.UNIQUE,
            "A persistent law grants 5% life steal and 8% overheal.", 0.00f, 1033),
    LEARNED_GRAVE(481, "The Learned Grave", Kind.EPIC,
            "A persistent law grants +40% experience gained and +10% healing received.", 0.00f, 1033),
    STONES_MEMORY(482, "Stone's Memory", Kind.EPIC,
            "A persistent law grants +45% mining speed and +2 armor piercing.", 0.00f, 1033),
    PERFECT_DEFLECTION(483, "Perfect Deflection", Kind.UNIQUE,
            "A persistent law grants +14% dodge chance and +12% healing received.", 0.00f, 1078),
    VEILED_PROTECTION(484, "Veiled Protection", Kind.EPIC,
            "A persistent law ignores 3 enchantment-protection points and shreds another 15% of protection.", 0.00f, 1078),
    HUNGER_FOR_THE_LIVING(485, "Hunger for the Living", Kind.UNIQUE,
            "A persistent law deals 2.5% of current health as bonus physical damage and grants 4% life steal.", 0.00f, 1116),

    RED_LAW(486, "The Red Law", Kind.DEATH,
            "One maximum heart is buried. Gain +18% critical chance and +65% critical damage, but receive 30% less healing.", 0.00f, 1148),
    NO_ARMOR_IS_SACRED(487, "No Armor Is Sacred", Kind.DEATH,
            "One maximum heart is buried. Ignore 8 armor, shred 25% more armor, and receive 15% less healing.", 0.00f, 1164),
    ARROW_OF_LAST_ACCOUNT(488, "Arrow of the Last Account", Kind.DEATH,
            "One maximum heart is buried. Gain +55% arrow damage, +40% velocity and +35% draw speed, but receive 15% less healing.", 0.00f, 1180),
    FROZEN_VERDICT(489, "Frozen Verdict", Kind.DEATH,
            "One maximum heart is buried. Attacks gain +6 cold magic damage and 3.5% current-health damage, but healing falls by 20%.", 0.00f, 1196),
    BURNING_VERDICT(490, "Burning Verdict", Kind.DEATH,
            "One maximum heart is buried. Attacks gain +6 fire magic damage and 3.5% current-health damage, but healing falls by 20%.", 0.00f, 1196),
    CRIMSON_USURY(491, "Crimson Usury", Kind.DEATH,
            "One maximum heart is buried. Gain 10% life steal and 12% overheal, but receive 15% less healing from all sources.", 0.00f, 1212),
    HOUSE_TAKES_EXPERIENCE(492, "The House Takes Experience", Kind.DEATH,
            "One maximum heart is buried. Experience gained doubles, but mining speed falls by 25% and healing received falls by 20%.", 0.00f, 1212),
    EMPTY_PLATE_DOCTRINE(493, "Empty Plate Doctrine", Kind.DEATH,
            "One maximum heart is buried. Gain +20% dodge and +12% critical chance, but receive 25% less healing.", 0.00f, 1228),
    BONEBREAKER_COVENANT(494, "Bonebreaker Covenant", Kind.DEATH,
            "One maximum heart is buried. Ignore 6 protection points and 4 armor, shred 30% protection, and receive 20% less healing.", 0.00f, 1244),
    BLOOD_IN_EXCESS(495, "Blood in Excess", Kind.DEATH,
            "One maximum heart is buried. Gain 15% overheal and 8% life steal, but arrow damage falls by 25%.", 0.00f, 1260),
    EXECUTIONER_OF_FULL_HEALTH(496, "Executioner of Full Health", Kind.DEATH,
            "One maximum heart is buried. Deal 7% current-health damage, gain +12% critical chance and +45% critical damage, but healing falls by 25%.", 0.00f, 1276),
    HOUSE_HAS_NUMBERS(497, "The House Has Numbers", Kind.DEATH,
            "One maximum heart is buried. Gain critical, ranged, mining, experience and dodge bonuses at once, but all healing received falls by 35%.", 0.00f, 1308),

    // Apothic Attributes second wave: hybrid timed clauses, reversible attribute burdens,
    // persistent doctrines, and a deeper Death-law pool.
    GLASS_RAZOR(498, "Glass Razor", Kind.WAGER,
            "For ninety seconds gain +10% critical chance, +35% critical damage and +3 armor piercing, but receive 12% less healing.", 0.00f, 682),
    BALLISTIC_PRAYER(499, "Ballistic Prayer", Kind.WAGER,
            "For two minutes arrows gain +24% damage, +22% velocity and ranged weapons draw 20% faster.", 0.00f, 700),
    BLACK_ICE_LEDGER(500, "The Black-Ice Ledger", Kind.WAGER,
            "For two minutes attacks gain +3 cold magic damage and shred 12% armor.", 0.00f, 718),
    ASHEN_VOLLEY(501, "Ashen Volley", Kind.WAGER,
            "For two minutes attacks gain +2.5 fire magic damage while arrows deal +22% damage.", 0.00f, 718),
    SURGEONS_MARGIN(502, "Surgeon's Margin", Kind.WAGER,
            "For ninety seconds gain 5% life steal and 3% current-health damage, but receive 15% less healing.", 0.00f, 736),
    GHOST_STEP_LEDGER(503, "Ghost-Step Ledger", Kind.WAGER,
            "For two minutes gain +10% dodge chance and +20% draw speed.", 0.00f, 736),
    PLUNDERED_LESSON(504, "The Plundered Lesson", Kind.WAGER,
            "For three minutes gain +45% experience and +30% mining speed, but receive 8% less healing.", 0.00f, 755),
    IRON_ALGEBRA(505, "Iron Algebra", Kind.WAGER,
            "For two minutes ignore 4 armor points and 3 enchantment-protection points.", 0.00f, 755),
    BREAKERS_INTEREST(506, "Breaker's Interest", Kind.WAGER,
            "For two minutes shred 14% armor and 20% enchantment protection.", 0.00f, 773),
    RED_RESERVOIR(507, "The Red Reservoir", Kind.WAGER,
            "For two minutes gain 7% life steal and 11% overheal.", 0.00f, 773),
    COLD_ACCOUNTING(508, "Cold Accounting", Kind.WAGER,
            "For two minutes attacks gain +3.5 cold magic damage and critical damage rises by 25%.", 0.00f, 791),
    CINDER_ACCOUNTING(509, "Cinder Accounting", Kind.WAGER,
            "For two minutes attacks gain +3.5 fire magic damage and critical chance rises by 9%.", 0.00f, 791),
    HIGH_VELOCITY_CLAUSE(510, "High-Velocity Clause", Kind.WAGER,
            "For two minutes arrows gain +30% velocity and +18% damage, but draw speed falls by 8%.", 0.00f, 809),
    SNIPERS_DEBT(511, "Sniper's Debt", Kind.WAGER,
            "For ninety seconds arrows deal +38% damage and 3% current-health damage, but ranged draw speed falls by 18%.", 0.00f, 827),
    QUICK_HAND_TAX(512, "Quick-Hand Tax", Kind.WAGER,
            "For two minutes draw speed rises by 45% and critical chance by 7%, but arrow damage falls by 10%.", 0.00f, 827),
    PALE_REFLEX(513, "Pale Reflex", Kind.WAGER,
            "For two minutes gain +9% dodge chance and +15% healing received.", 0.00f, 845),
    QUARRY_FEVER(514, "Quarry Fever", Kind.WAGER,
            "For three minutes mining speed rises by 65% and experience by 25%, but dodge chance falls by 6%.", 0.00f, 845),
    EDUCATED_VIOLENCE(515, "Educated Violence", Kind.WAGER,
            "For two minutes experience gained rises by 35% and critical chance rises by 10%.", 0.00f, 864),
    MERCYS_INTEREST(516, "Mercy's Interest", Kind.WAGER,
            "For two minutes gain +20% healing received and 10% overheal, but current-health damage falls by 1.5%.", 0.00f, 864),
    HEMORRHAGE_CLAUSE(517, "Hemorrhage Clause", Kind.WAGER,
            "For ninety seconds gain 5% current-health damage and +35% critical damage, but receive 18% less healing.", 0.00f, 882),
    DEFLECTORS_WAGE(518, "Deflector's Wage", Kind.WAGER,
            "For two minutes gain +11% dodge chance and shred 12% enchantment protection.", 0.00f, 882),
    PICK_AND_BLADE(519, "Pick and Blade", Kind.WAGER,
            "For two minutes gain +40% mining speed and +3 armor piercing.", 0.00f, 900),
    PREDATORS_ARITHMETIC(520, "Predator's Arithmetic", Kind.WAGER,
            "For ninety seconds deal 4% current-health damage and arrows gain +18% damage.", 0.00f, 922),
    HOUSE_ACCELERANT(521, "House Accelerant", Kind.WAGER,
            "For ninety seconds gain +20% draw speed, +8% critical chance and +25% mining speed, but receive 12% less healing.", 0.00f, 944),

    PAPER_SKIN(522, "Paper Skin", Kind.SCAR,
            "Permanently gain +16% dodge chance, but receive 35% less healing until the skin is bound.", 0.00f, 809),
    BIND_THE_SKIN(523, "Bind the Skin", Kind.REMEDY,
            "Remove Paper Skin and restore the normal dodge/healing balance.", 0.00f, 809),
    FROZEN_MARROW(524, "Frozen Marrow", Kind.CURSE,
            "Permanently gain +4 cold magic damage, but draw speed falls by 20% and healing by 15% until the marrow is warmed.", 0.00f, 845),
    WARM_THE_MARROW(525, "Warm the Marrow", Kind.REMEDY,
            "Remove Frozen Marrow and its cold/draw-speed exchange.", 0.00f, 845),
    CINDERED_NERVE(526, "Cindered Nerve", Kind.SCAR,
            "Permanently gain +4 fire magic damage and +8% critical chance, but dodge falls by 10% until the nerve is cooled.", 0.00f, 882),
    COOL_THE_NERVE(527, "Cool the Nerve", Kind.REMEDY,
            "Remove Cindered Nerve and restore the normal fire/dodge balance.", 0.00f, 882),
    GREEDY_PICK(528, "The Greedy Pick", Kind.CURSE,
            "Permanently gain +70% mining speed and +35% experience, but dodge falls by 8% and healing by 15% until greed is broken.", 0.00f, 922),
    BREAK_THE_GREED(529, "Break the Greed", Kind.REMEDY,
            "Remove The Greedy Pick and its mining/experience bargain.", 0.00f, 922),
    SERRATED_MEMORY(530, "Serrated Memory", Kind.SCAR,
            "Permanently shred 20% armor and 12% protection, but critical damage falls by 20% until the memory is filed smooth.", 0.00f, 967),
    FILE_THE_MEMORY(531, "File the Memory", Kind.REMEDY,
            "Remove Serrated Memory and restore the normal shred/critical balance.", 0.00f, 967),
    HUNGRY_QUIVER(532, "The Hungry Quiver", Kind.CURSE,
            "Permanently gain +45% arrow damage and +25% velocity, but draw speed falls by 30% and healing by 15% until the quiver is fed.", 0.00f, 1011),
    FEED_THE_QUIVER(533, "Feed the Quiver", Kind.REMEDY,
            "Remove The Hungry Quiver and its ranged penalties.", 0.00f, 1011),

    BLACK_LANCET(534, "The Black Lancet", Kind.EPIC,
            "A persistent law grants +4 armor piercing and 3% current-health damage.", 0.00f, 1078),
    MERCILESS_GEOMETRY(535, "Merciless Geometry", Kind.UNIQUE,
            "A persistent law grants +10% critical chance, +30% critical damage and +2 protection piercing.", 0.00f, 1116),
    WINTER_ENGINE(536, "The Winter Engine", Kind.EPIC,
            "A persistent law grants +4 cold damage, +15% draw speed and +5% dodge.", 0.00f, 1116),
    CINDER_ENGINE(537, "The Cinder Engine", Kind.EPIC,
            "A persistent law grants +4 fire damage and +22% arrow damage.", 0.00f, 1116),
    VAMPIRE_LEDGER(538, "The Vampire Ledger", Kind.UNIQUE,
            "A persistent law grants 7% life steal, 10% overheal and +8% healing received.", 0.00f, 1148),
    MASTER_QUARRY(539, "Master Quarry", Kind.EPIC,
            "A persistent law grants +55% mining speed, +30% experience and +2 armor piercing.", 0.00f, 1148),
    ABSENT_TARGET(540, "The Absent Target", Kind.UNIQUE,
            "A persistent law grants +16% dodge chance and +7% critical chance.", 0.00f, 1180),
    PERFECT_TRAJECTORY(541, "Perfect Trajectory", Kind.UNIQUE,
            "A persistent law grants +36% arrow damage, +20% velocity and +20% draw speed.", 0.00f, 1180),
    PROFANED_AEGIS(542, "Profaned Aegis", Kind.EPIC,
            "A persistent law shreds 16% armor and 20% enchantment protection while ignoring 2 protection points.", 0.00f, 1212),
    RED_INSTRUCTION(543, "The Red Instruction", Kind.UNIQUE,
            "A persistent law grants 3.5% current-health damage and +40% critical damage.", 0.00f, 1212),
    PATIENT_HAND(544, "The Patient Hand", Kind.EPIC,
            "A persistent law grants +25% draw speed, +7% critical chance and +8% healing received.", 0.00f, 1244),
    HOUSE_COMPOUND(545, "The House Compound", Kind.UNIQUE,
            "A persistent law grants +2 armor piercing, +6% critical chance, +5% dodge and +25% experience gained.", 0.00f, 1276),

    GLASS_SOVEREIGN(546, "The Glass Sovereign", Kind.DEATH,
            "One maximum heart is buried. Gain +25% critical chance and +100% critical damage, but receive 45% less healing.", 0.00f, 1308),
    TEETH_BEHIND_STEEL(547, "Teeth Behind Steel", Kind.DEATH,
            "One maximum heart is buried. Ignore 10 armor and 5 protection, deal 4% current-health damage, but receive 25% less healing.", 0.00f, 1324),
    TERMINAL_BALLISTICS(548, "Terminal Ballistics", Kind.DEATH,
            "One maximum heart is buried. Gain +75% arrow damage, +50% velocity and +50% draw speed, but lose 10% dodge and 25% healing.", 0.00f, 1340),
    WINTER_OWNS_BLOOD(549, "Winter Owns the Blood", Kind.DEATH,
            "One maximum heart is buried. Gain +8 cold damage, 5% life steal and 4% current-health damage, but healing falls by 30%.", 0.00f, 1356),
    ASH_OWNS_BREATH(550, "Ash Owns the Breath", Kind.DEATH,
            "One maximum heart is buried. Gain +8 fire damage, +12% critical chance and 4% current-health damage, but healing falls by 30%.", 0.00f, 1356),
    RED_RESERVOIR_LAW(551, "The Red Reservoir Law", Kind.DEATH,
            "One maximum heart is buried. Gain 12% life steal and 20% overheal, but receive 20% less healing from other sources.", 0.00f, 1372),
    FINAL_QUARRY(552, "The Final Quarry", Kind.DEATH,
            "One maximum heart is buried. Gain +100% mining speed, +75% experience and +5 armor piercing, but lose 10% dodge and 20% healing.", 0.00f, 1388),
    UNTOUCHABLE_DEBT(553, "Untouchable Debt", Kind.DEATH,
            "One maximum heart is buried. Gain +25% dodge and +25% draw speed, but receive 40% less healing.", 0.00f, 1404),
    SHREDDERS_CROWN(554, "The Shredder's Crown", Kind.DEATH,
            "One maximum heart is buried. Shred 35% armor and 35% protection and gain +10% critical chance, but healing falls by 25%.", 0.00f, 1420),
    OPENING_EXECUTION(555, "Opening Execution", Kind.DEATH,
            "One maximum heart is buried. Deal 10% current-health damage and gain +50% critical damage, but healing falls by 35%.", 0.00f, 1436),
    LAST_QUIVER(556, "The Last Quiver", Kind.DEATH,
            "One maximum heart is buried. Gain +65% arrow damage, +15% critical chance and +40% critical damage, but draw speed and healing are reduced.", 0.00f, 1452),
    HOUSE_REWRITES_BODY(557, "The House Rewrites the Body", Kind.DEATH,
            "One maximum heart is buried. Gain a broad suite of penetration, shred, critical, dodge, life-steal and experience bonuses, but healing falls by 45%.", 0.00f, 1500),


    // Apothic Attributes third wave: reactive clauses, conditional build laws,
    // reversible body bargains, Death mechanics, and rare House-joke encounters.
    KILLING_MOMENTUM(558, "Killing Momentum", Kind.WAGER,
            "For ninety seconds gain +8% critical chance, +20% critical damage and 4% life steal. Hostile kills briefly accelerate you.", 0.00f, 755),
    FIRST_CUT_CLAUSE(559, "First-Cut Clause", Kind.WAGER,
            "For ninety seconds deal 4% current-health damage and ignore 2 armor. The clause is naturally strongest against untouched prey.", 0.00f, 773),
    LAST_BREATH_MARGIN(560, "Last-Breath Margin", Kind.WAGER,
            "For ninety seconds gain +12% dodge and 6% life steal but receive 20% less healing. Below one-quarter health, panic becomes power.", 0.00f, 791),
    STILL_BALLISTICS(561, "Still Ballistics", Kind.WAGER,
            "For two minutes gain +32% arrow damage, +20% velocity and +15% draw speed. Holding still briefly sharpens the shot further.", 0.00f, 809),
    MOVING_TARGET_CLAUSE(562, "Moving-Target Clause", Kind.WAGER,
            "For two minutes gain +10% dodge and +25% arrow velocity. Sprinting keeps the House from getting a clean measurement.", 0.00f, 809),
    QUARRY_TEMPER(563, "Quarry Temper", Kind.WAGER,
            "For three minutes gain +55% mining speed and +8% critical chance. Breaking ore briefly turns labor into violence.", 0.00f, 827),
    FURNACE_ARITHMETIC(564, "Furnace Arithmetic", Kind.WAGER,
            "For two minutes gain +4 fire damage and 5% life steal but receive 10% less healing. Burning yourself briefly increases the return.", 0.00f, 845),
    WINTER_PULSE(565, "Winter Pulse", Kind.WAGER,
            "For two minutes gain +4 cold damage and +8% dodge. Rain and water briefly make your movement unnaturally light.", 0.00f, 845),
    EMPTY_STOMACH_DOCTRINE(566, "Empty-Stomach Doctrine", Kind.WAGER,
            "For two minutes deal 5% current-health damage and gain +20% critical damage but receive 15% less healing. Hunger sharpens the clause.", 0.00f, 864),
    FULL_STOMACH_DIVIDEND(567, "Full-Stomach Dividend", Kind.WAGER,
            "For two minutes gain +20% healing received, 12% overheal and +25% experience. A full hunger bar pays a small regeneration dividend.", 0.00f, 864),
    MOONSHOT_RECEIPT(568, "Moonshot Receipt", Kind.WAGER,
            "For two minutes arrows gain +35% damage and +30% velocity. Nighttime turns the receipt into a hunter's instrument.", 0.00f, 882),
    SUNBURN_LEDGER(569, "Sunburn Ledger", Kind.WAGER,
            "For two minutes gain +4 fire damage and shred 16% armor. Open daylight briefly quickens your stride.", 0.00f, 882),
    DUELISTS_EXCEPTION(570, "Duelist's Exception", Kind.WAGER,
            "For two minutes gain +10% critical chance, +30% critical damage and +8% dodge. Exactly one nearby hostile activates the exception.", 0.00f, 900),
    MOB_INTEREST(571, "Mob Interest", Kind.WAGER,
            "For two minutes shred 18% armor and 12% protection and gain 5% life steal. Four nearby hostiles make the House insure you briefly.", 0.00f, 900),
    BROKEN_SHIELD_PREMIUM(572, "Broken-Shield Premium", Kind.WAGER,
            "For two minutes ignore 5 armor and gain +12% critical chance. An empty offhand increases the premium.", 0.00f, 922),
    HEAVY_POCKETS_CLAUSE(573, "Heavy-Pockets Clause", Kind.WAGER,
            "For three minutes gain +45% mining speed and +40% experience. A nearly full inventory makes the paperwork somehow more efficient.", 0.00f, 922),
    CLEAN_HANDS_CLAUSE(574, "Clean-Hands Clause", Kind.WAGER,
            "For two minutes gain +16% dodge and +12% critical chance but receive 20% less healing. Wearing no armor briefly grants speed.", 0.00f, 944),
    PLATED_DEBT(575, "Plated Debt", Kind.WAGER,
            "For two minutes ignore 5 armor, shred 15% armor and ignore 3 protection, but draw speed falls by 15%. A full armor set steadies you.", 0.00f, 944),

    BLOODHOUND_CALCULUS(576, "Bloodhound Calculus", Kind.EPIC,
            "A persistent law grants +6% critical chance and +25% critical damage. Repeated hostile kills build short hunting surges.", 0.00f, 1033),
    HUNTERS_RHYTHM(577, "Hunter's Rhythm", Kind.UNIQUE,
            "A persistent law grants +30% arrow damage and +20% draw speed. Projectile kills keep the rhythm moving.", 0.00f, 1078),
    EXECUTIONER_CLOCK(578, "Executioner Clock", Kind.EPIC,
            "A persistent law deals 4% current-health damage and ignores 4 armor. The first direct hit after a calm interval receives an extra execution stroke.", 0.00f, 1100),
    REDIRECTION_LAW(579, "Law of Redirection", Kind.UNIQUE,
            "A persistent law grants +12% dodge and +10% healing received. Taking a hit arms a stronger answer on your next direct strike.", 0.00f, 1116),
    GLASS_CANNON_ARCHIVE(580, "Glass-Cannon Archive", Kind.EPIC,
            "A persistent law grants +15% critical chance and +55% critical damage but reduces healing received by 20%.", 0.00f, 1132),
    QUARRY_COMMUNION(581, "Quarry Communion", Kind.EPIC,
            "A persistent law grants +60% mining speed and +30% experience. Ore breaks periodically leave a brief protective afterimage.", 0.00f, 1148),
    EMBER_DEBT_COLLECTOR(582, "Ember Debt Collector", Kind.UNIQUE,
            "A persistent law grants +4 fire damage and 6% overheal. Burning kills collect a small absorption payment.", 0.00f, 1164),
    RIME_COLLECTOR(583, "Rime Collector", Kind.UNIQUE,
            "A persistent law grants +4 cold damage and +6% dodge. Wet hostile kills accumulate rime; every third collection chills the crowd.", 0.00f, 1180),
    LONE_PREDATOR(584, "Lone Predator", Kind.EPIC,
            "A persistent law grants +10% critical chance and 4% current-health damage. Exactly one nearby hostile sharpens the hunt.", 0.00f, 1196),
    CROWD_AUDITOR(585, "Crowd Auditor", Kind.EPIC,
            "A persistent law grants +12% dodge and shreds 18% protection. Five or more nearby hostiles trigger a defensive audit.", 0.00f, 1212),
    FULL_QUIVER_LAW(586, "Law of the Full Quiver", Kind.UNIQUE,
            "A persistent law grants +35% arrow damage, +25% velocity and +20% draw speed. Projectile kills occasionally refund an arrow.", 0.00f, 1228),
    VULTURES_MARGIN(587, "Vulture's Margin", Kind.EPIC,
            "A persistent law grants 4% life steal and +20% experience. Hostile kills return a small scavenger's dividend.", 0.00f, 1244),

    GLASS_BLOOD(588, "Glass Blood", Kind.CURSE,
            "Permanently gain +15% critical chance and +60% critical damage but receive 35% less healing until the blood is thickened.", 0.00f, 922),
    THICKEN_GLASS_BLOOD(589, "Thicken the Glass Blood", Kind.REMEDY,
            "Remove Glass Blood and restore normal critical/healing balance.", 0.00f, 922),
    LEAD_FINGERS(590, "Lead Fingers", Kind.SCAR,
            "Permanently ignore 6 armor, but ranged draw speed falls by 35% until your fingers are unclasped.", 0.00f, 944),
    UNCLASP_FINGERS(591, "Unclasp the Fingers", Kind.REMEDY,
            "Remove Lead Fingers and restore normal armor-pierce/draw-speed balance.", 0.00f, 944),
    WHITE_HOT_MARROW(592, "White-Hot Marrow", Kind.CURSE,
            "Permanently gain +6 fire damage, but lose 12% dodge and 15% healing received until the marrow is quenched.", 0.00f, 967),
    QUENCH_WHITE_HOT_MARROW(593, "Quench the White-Hot Marrow", Kind.REMEDY,
            "Remove White-Hot Marrow and its fire/dodge/healing exchange.", 0.00f, 967),
    DEAD_WINTER_NERVE(594, "Dead-Winter Nerve", Kind.SCAR,
            "Permanently gain +6 cold damage and +10% dodge, but ranged draw speed falls by 20% until the nerve is awakened.", 0.00f, 989),
    WAKE_WINTER_NERVE(595, "Wake the Winter Nerve", Kind.REMEDY,
            "Remove Dead-Winter Nerve and restore normal cold/dodge/draw balance.", 0.00f, 989),
    AUDITORS_HUNGER(596, "Auditor's Hunger", Kind.CURSE,
            "Permanently gain +70% experience and 5% current-health damage, but receive 25% less healing and hostile kills make you hungry until the audit is closed.", 0.00f, 1011),
    CLOSE_THE_AUDIT(597, "Close the Audit", Kind.REMEDY,
            "Remove Auditor's Hunger and its appetite for combat paperwork.", 0.00f, 1011),
    HOLLOW_AIM(598, "Hollow Aim", Kind.SCAR,
            "Permanently gain +50% arrow damage and 4% current-health damage, but draw speed falls by 30% and direct melee damage is reduced until the aim is corrected.", 0.00f, 1033),
    CORRECT_THE_AIM(599, "Correct the Aim", Kind.REMEDY,
            "Remove Hollow Aim and restore normal ranged/melee balance.", 0.00f, 1033),
    BORROWED_SKIN(600, "Borrowed Skin", Kind.CURSE,
            "Permanently gain +20% dodge but receive 20% less healing. When an attack does land, the borrowed skin briefly weakens you.", 0.00f, 1056),
    RETURN_THE_SKIN(601, "Return the Skin", Kind.REMEDY,
            "Remove Borrowed Skin and its dodge/weakness bargain.", 0.00f, 1056),
    RED_QUARRY(602, "The Red Quarry", Kind.SCAR,
            "Permanently gain +100% mining speed and +50% experience. Breaking ore periodically takes a small blood payment until the quarry is cooled.", 0.00f, 1078),
    COOL_THE_QUARRY(603, "Cool the Quarry", Kind.REMEDY,
            "Remove The Red Quarry and end its blood payment.", 0.00f, 1078),

    REDLINE_ANATOMY(604, "Redline Anatomy", Kind.DEATH,
            "One maximum heart is buried. Gain +20% critical chance, +80% critical damage and 10% life steal but receive 40% less healing. Low health triggers a redline surge.", 0.00f, 1340),
    PERFECT_MURDER_GEOMETRY(605, "Perfect Murder Geometry", Kind.DEATH,
            "One maximum heart is buried. Ignore 10 armor, shred 35% armor and deal 8% current-health damage but receive 25% less healing. Solitary prey activates the proof.", 0.00f, 1356),
    METEOR_QUIVER(606, "Meteor Quiver", Kind.DEATH,
            "One maximum heart is buried. Gain +80% arrow damage, +45% velocity and +20% critical chance but receive 30% less healing. Projectile hits periodically burst around the target.", 0.00f, 1372),
    HOUSE_BLOOD_BANK(607, "The House Blood Bank", Kind.DEATH,
            "One maximum heart is buried. Gain 15% life steal, 25% overheal and +10% healing received. Large absorption reserves briefly improve your offense.", 0.00f, 1388),
    IMPOSSIBLE_WINTER(608, "Impossible Winter", Kind.DEATH,
            "One maximum heart is buried. Gain +10 cold damage and 6% current-health damage but receive 30% less healing. Wet kills spread a freezing collection notice.", 0.00f, 1404),
    CREMATION_CLAUSE(609, "Cremation Clause", Kind.DEATH,
            "One maximum heart is buried. Gain +10 fire damage and shred 20% armor but receive 30% less healing. Burning kills ignite nearby hostiles.", 0.00f, 1420),
    QUARRY_OF_FLESH(610, "Quarry of Flesh", Kind.DEATH,
            "One maximum heart is buried. Gain +100% mining speed, +100% experience and ignore 6 armor but receive 25% less healing. Hostile kills feed a strange material tally.", 0.00f, 1436),
    LAST_ACCOUNT(611, "The Last Account", Kind.DEATH,
            "One maximum heart is buried. Gain +12% critical chance, +45% critical damage and 5% current-health damage but receive 30% less healing. Every twenty-five hostile kills produces one netherite scrap.", 0.00f, 1468),

    CRITICAL_LOTTERY(612, "Critical Lottery", Kind.UNIQUE,
            "A persistent law grants +5% critical chance and +20% critical damage. Direct hits may pay a spectacular bonus—or charge a small handling fee.", 0.00f, 1180),
    ECHO_CHAMBER(613, "The Echo Chamber", Kind.UNIQUE,
            "A persistent law grants +3 armor piercing and +20% critical damage. Every fourth direct hit echoes a fraction of itself.", 0.00f, 1212),
    HOUSE_FAVORITE_SEVEN(614, "The House's Favorite Number", Kind.EPIC,
            "A persistent law grants +7% critical chance and +7% dodge. Every seventh hostile kill grants exactly seven seconds of unreasonable confidence.", 0.00f, 1244),
    LAST_ARROW_IN_QUIVER(615, "The Last Arrow in the Quiver", Kind.UNIQUE,
            "A persistent law grants +50% arrow damage and +30% critical damage. If your inventory truly contains one arrow, projectile damage receives a final premium.", 0.00f, 1276),
    MERCY_AFTER_MURDER(616, "Mercy After Murder", Kind.EPIC,
            "A persistent law grants 5% life steal and +15% healing received. Hostile kills periodically grant a brief regeneration mercy.", 0.00f, 1292),
    REVOLVING_DOOR(617, "The Revolving Door", Kind.UNIQUE,
            "A persistent law grants +8% dodge and +15% draw speed. When a hit gets through, the House occasionally shows you the exit for two seconds.", 0.00f, 1308),

    // Rare joke/easter-egg cards. Their text is intentionally literal but incomplete.
    ZOMBIE_ONE_V_ONE(618, "Zombie 1v1", Kind.WAGER,
            "Fight one zombie. Win, and the House will pay a valuable reward. No additional details are required.", 0.00f, 864),
    FREE_DIAMOND(619, "Free Diamond", Kind.WAGER,
            "Receive one diamond. Completely free. The House has checked the wording twice.", 0.00f, 755),
    ONE_SKELETON(620, "One Skeleton", Kind.WAGER,
            "Defeat one skeleton. A valuable reward will be issued afterward. It is, technically, one skeleton.", 0.00f, 944),
    QUICK_MINING_JOB(621, "Quick Mining Job", Kind.WAGER,
            "Mine one piece of natural stone and receive one diamond. This is a remarkably short job.", 0.00f, 718),
    SHORT_WALK(622, "A Short Walk", Kind.WAGER,
            "Walk twelve blocks. Receive two emerald blocks. Distance is measured correctly; destination was never specified.", 0.00f, 791),
    FREE_HEALING(623, "Free Healing", Kind.WAGER,
            "Heal to full immediately. There is no monetary charge for this medical service.", 0.00f, 827),
    ONE_BABY_ZOMBIE(624, "One Baby Zombie", Kind.WAGER,
            "Defeat one baby zombie. The adjective describes its age, not its professional qualifications.", 0.00f, 1033),
    LUCKY_SEVEN(625, "Lucky Seven", Kind.WAGER,
            "Defeat seven hostiles and receive seven diamonds. The number seven will remain involved throughout the transaction.", 0.00f, 1078),
    NOTHING_HAPPENS(626, "Nothing Happens", Kind.WAGER,
            "Nothing happens. This clause is exceptionally confident about that statement.", 0.00f, 1148),
    SMALL_REWARD(627, "A Small Reward", Kind.WAGER,
            "Receive one netherite scrap. The reward is small. Delivery conditions are not discussed.", 0.00f, 1180),

    ANOMALY_GILDED(165, "Gilded Anomaly", Kind.UNIQUE,
            "Its contents cannot be read before signature.", 0.00f, 140),
    ANOMALY_FERAL(166, "Feral Anomaly", Kind.UNIQUE,
            "Its contents cannot be read before signature.", 0.00f, 240),
    ANOMALY_HOLLOW(167, "Hollow Anomaly", Kind.UNIQUE,
            "Its contents cannot be read before signature.", 0.00f, 360),
    ANOMALY_STATIC(168, "Static Anomaly", Kind.UNIQUE,
            "Its contents cannot be read before signature.", 0.00f, 520),
    ANOMALY_MIRROR(169, "Mirror Anomaly", Kind.UNIQUE,
            "Its contents cannot be read before signature.", 0.00f, 760),
    ANOMALY_BLACK(170, "Black Anomaly", Kind.UNIQUE,
            "Its contents cannot be read before signature.", 0.00f, 1050);

    public enum Kind { DEBT, WAGER, SCAR, REMEDY, CONTRACT, RITUAL, COVENANT, MASTER, EPIC, UNIQUE, CURSE, DEATH, REFRESH }

    static {
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (ForbiddenBargain card : values()) {
            if (card.id < 0 || !ids.add(card.id)) {
                throw new IllegalStateException("Duplicate/invalid ForbiddenBargain id: " + card.id);
            }
        }
    }

    public final int id;
    public final String title;
    public final Kind kind;
    public final String debtText;
    private final float fixedReward;
    /** Additional per-card progression gate inside its broad rarity shelf. */
    public final int minResolved;

    ForbiddenBargain(int id, String title, Kind kind, String debtText, float fixedReward) {
        this(id, title, kind, debtText, fixedReward, 0);
    }

    ForbiddenBargain(int id, String title, Kind kind, String debtText, float fixedReward, int minResolved) {
        this.id = id;
        this.title = title;
        this.kind = kind;
        this.debtText = debtText;
        this.fixedReward = fixedReward;
        this.minResolved = Math.max(0, minResolved);
    }

    public float rewardAdd() {
        return switch (this) {
            case BORROWED_BREATH -> WardConfig.bargainBorrowedBreathReward;
            case IRON_DEBT -> WardConfig.bargainIronDebtReward;
            case WATCHING_MARK -> WardConfig.bargainWatchingMarkReward;
            default -> fixedReward;
        };
    }

    public boolean isRemedy() { return kind == Kind.REMEDY; }
    public boolean isCurse() { return kind == Kind.CURSE; }
    public boolean isEpic() { return kind == Kind.EPIC; }
    public boolean isMaster() { return kind == Kind.MASTER; }
    public boolean isUnique() { return kind == Kind.UNIQUE; }
    public boolean isDeath() { return kind == Kind.DEATH; }
    public boolean isContract() { return kind == Kind.CONTRACT; }
    public boolean isRitual() { return kind == Kind.RITUAL; }
    public boolean isCovenant() { return kind == Kind.COVENANT; }
    public boolean isObjectiveCard() { return !CardEchoSystem.isPrimer(this) && (isContract() || isRitual() || isCovenant()); }

    /** Cards primarily intended to change ordinary Minecraft play instead of the next ward. */
    public boolean isWorldPlay() {
        return id >= 35 || kind == Kind.CONTRACT || kind == Kind.RITUAL || kind == Kind.COVENANT
                || kind == Kind.MASTER || kind == Kind.EPIC || kind == Kind.UNIQUE || kind == Kind.DEATH;
    }

    public boolean matchesSignature(MasterSignature signature) {
        return switch (this) {
            case CROOKED_PRIVATE -> signature == MasterSignature.CROOKED;
            case VEILED_PRIVATE -> signature == MasterSignature.VEILED;
            case EXACTING_PRIVATE -> signature == MasterSignature.EXACTING;
            default -> true;
        };
    }

    /** Broad progression shelf shared by every normal card delivery path. */
    public static boolean kindShelfUnlocked(Kind kind, int resolved) {
        return switch (kind) {
            case MASTER -> resolved >= WardConfig.masterCardsAfterBeaten;
            case CONTRACT -> resolved >= WardConfig.contractCardsAfterBeaten;
            case RITUAL -> resolved >= WardConfig.ritualCardsAfterBeaten;
            case COVENANT -> resolved >= WardConfig.covenantCardsAfterBeaten;
            case CURSE -> resolved >= WardConfig.curseCardsAfterBeaten;
            case EPIC -> resolved >= WardConfig.epicCardsAfterBeaten;
            case UNIQUE -> resolved >= WardConfig.uniqueCardsAfterBeaten;
            case DEATH -> resolved >= WardConfig.deathCardsAfterBeaten;
            case DEBT, WAGER, SCAR, REMEDY, REFRESH -> resolved >= WardConfig.normalCardsAfterBeaten;
        };
    }

    /** Whether offering this card can change anything for this player now. */
    public boolean available(LockData data, UUID player, boolean watcherEligible) {
        int resolved = data.totalBeaten(player);
        if (!kindShelfUnlocked(kind, resolved) || resolved < minResolved) return false;
        return switch (this) {
            case BORROWED_BREATH -> !data.hasBorrowedBreath(player);
            case IRON_DEBT -> !data.hasIronDebt(player);
            case WATCHING_MARK -> watcherEligible && !data.hasWatchingMark(player);
            case CRIMSON_BALANCE -> !data.hasCrimsonBalance(player);
            case SEVERED_MEASURE -> data.heartDebt(player) < 3;
            case LAST_CANDLE -> !data.hasLastCandle(player);
            case GLASS_NERVE -> !data.hasGlassNerve(player);
            case PALE_COVENANT -> !data.hasPaleCovenant(player);
            case OPEN_VEIN -> !data.hasOpenVein(player) && data.heartDebt(player) < 3;
            case THIN_BLOOD -> !data.hasThinBlood(player);
            case LOADED_DICE -> data.loadedDiceCharges(player) <= 0;
            case MERCYS_DUE -> data.mercysDueCharges(player) <= 0;
            case STILL_HEART -> !data.hasStillHeart(player);
            case RECONCILED_BLOOD -> data.hasCrimsonBalance(player);
            case HEART_RETURNED -> data.heartDebt(player) > 0;
            case NERVE_SETTLED -> data.hasGlassNerve(player);
            case COVENANT_BROKEN -> data.hasPaleCovenant(player);
            case HEART_AWAKENED -> data.hasStillHeart(player);
            case DEBT_UNWRITTEN -> data.hasBorrowedBreath(player) || data.hasIronDebt(player)
                    || data.hasWatchingMark(player) || data.curseLoot15Charges(player) > 0
                    || data.hasAshenTongue(player) || data.hasBrittlePilgrimage(player) || data.hasBloodTithe(player)
                    || data.hasUnique(player, "shivering_tithe") || FreshCardEffects.hasLesserBurden(data, player)
                    || SecondWaveCardEffects.hasLesserBurden(data, player) || ThirdWaveCardEffects.hasLesserBurden(data, player)
                    || FifthWaveCardEffects.hasLesserBurden(data, player) || ApothicCardEffects.hasLesserBurden(data, player)
                    || data.uniqueLong(player, "momentum_until") != Long.MIN_VALUE
                    || data.uniqueLong(player, "quicksilver_until") != Long.MIN_VALUE;
            case THICKENED_BLOOD -> data.hasThinBlood(player);
            case CROOKED_PRIVATE -> data.masterPact(player) != MasterSignature.CROOKED.ordinal();
            case VEILED_PRIVATE -> data.masterPact(player) != MasterSignature.VEILED.ordinal();
            case EXACTING_PRIVATE -> data.masterPact(player) != MasterSignature.EXACTING.ordinal();
            case BLACK_DIVIDEND -> !data.hasBlackHarvest(player);
            case SECOND_LEDGER -> !data.hasSecondEntry(player);
            case ABSOLUTION -> data.hasAnyBargainDebt(player) || FreshCardEffects.hasLesserBurden(data, player) || SecondWaveCardEffects.hasLesserBurden(data, player) || ThirdWaveCardEffects.hasLesserBurden(data, player) || FifthWaveCardEffects.hasLesserBurden(data, player) || ApothicCardEffects.hasLesserBurden(data, player);
            case BLOOD_TITHE -> !data.hasBloodTithe(player) && data.heartDebt(player) < 3;
            case DIMINISHED_SHARE -> data.curseLoot15Charges(player) <= 0;
            case FRAIL_HAND -> !data.hasBrittlePilgrimage(player);
            case ASHEN_TONGUE -> !data.hasAshenTongue(player);
            case REFRESH_HAND -> true;
            case MOONLIT_HUNT -> !data.hasUnique(player, "moonlit_hunt");
            case RED_MARCH -> !data.hasUnique(player, "red_march");
            case FIRST_SUPPER -> !data.hasUnique(player, "first_supper");
            case LAST_WITNESS -> !data.hasUnique(player, "last_witness");
            case EMBER_COUNT -> data.uniqueInt(player, "ember_hits") <= 0;
            case ORE_WHISPER -> data.uniqueLong(player, "ore_whisper_until") == Long.MIN_VALUE;
            case BORROWED_MOMENTUM -> data.uniqueLong(player, "momentum_until") == Long.MIN_VALUE;
            case HUNTERS_DIVIDEND -> data.uniqueInt(player, "hunter_dividend") <= 0;
            case BELLGLASS_SIGHT -> !data.hasUnique(player, "bellglass_sight");
            case COAL_KISS -> data.uniqueInt(player, "coal_kiss_hits") <= 0;
            case THORN_LEDGER -> !data.hasUnique(player, "thorn_ledger");
            case HEARTHMARK -> data.uniqueInt(player, "hearthmark_meals") <= 0;
            case SALT_CIRCLE -> data.uniqueInt(player, "salt_circle") <= 0;
            case DUSTBOUND_SOLES -> !data.hasUnique(player, "dustbound_soles");
            case BLACK_COMPASS -> data.uniqueLong(player, "black_compass_until") == Long.MIN_VALUE;
            case LANTERN_BLOOD -> !data.hasUnique(player, "lantern_blood");
            case IRON_ECHO -> data.uniqueInt(player, "iron_echo_hits") <= 0;
            case GRAVE_RATION -> data.uniqueInt(player, "grave_ration") <= 0;
            case POCKET_ECLIPSE -> data.uniqueLong(player, "pocket_eclipse_until") == Long.MIN_VALUE;
            case PILGRIMS_LUCK -> data.uniqueInt(player, "pilgrims_luck_blocks") <= 0;
            case MEMENTO_MORI -> !data.hasUnique(player, "memento_mori") && data.heartDebt(player) <= 1;
            case BLACK_SUN -> !data.hasUnique(player, "black_sun");
            case COFFIN_ROAD -> !data.hasUnique(player, "coffin_road");
            case GRAVE_BELL -> !data.hasUnique(player, "grave_bell");
            case SHATTER_BELLGLASS -> data.hasUnique(player, "bellglass_sight");
            case PRUNE_THORNS -> data.hasUnique(player, "thorn_ledger");
            case CUT_DUST_BINDING -> data.hasUnique(player, "dustbound_soles");
            case SNUFF_LANTERN -> data.hasUnique(player, "lantern_blood");
            case ROTTEN_LEDGER -> canStartObjective(data, player, "rotten_ledger");
            case BONE_TALLY -> canStartObjective(data, player, "bone_tally");
            case CREEPER_CLAUSE -> canStartObjective(data, player, "creeper_clause");
            case SILK_WARRANT -> canStartObjective(data, player, "silk_warrant");
            case ENDER_AUDIT -> canStartObjective(data, player, "ender_audit");
            case WITCH_LEDGER -> canStartObjective(data, player, "witch_ledger");
            case STONE_COMMUNION -> canStartObjective(data, player, "stone_communion");
            case VEIN_LITANY -> canStartObjective(data, player, "vein_litany");
            case WOODEN_CONFESSION -> canStartObjective(data, player, "wooden_confession");
            case EARTHEN_VIGIL -> canStartObjective(data, player, "earthen_vigil");
            case OBSIDIAN_PRAYER -> canStartObjective(data, player, "obsidian_prayer");
            case RED_CENSUS -> canStartObjective(data, player, "red_census");
            case IRON_SILENCE -> canStartObjective(data, player, "iron_silence");
            case INNOCENCE_TAX -> canStartObjective(data, player, "innocence_tax");
            case WITCHFIRE_TITHE -> canStartObjective(data, player, "witchfire_tithe");
            case VEIN_DRINKER -> data.uniqueInt(player, "vein_drinker") <= 0;
            case QUICKSILVER_PRAYER -> data.uniqueLong(player, "quicksilver_until") == Long.MIN_VALUE;
            case FERRYMAN_LEDGER -> data.uniqueInt(player, "ferryman_guard") <= 0;
            case CINDER_VOW -> !data.hasUnique(player, "cinder_vow");
            case SHIVERING_TITHE -> !data.hasUnique(player, "shivering_tithe");
            case THAW_THE_TITHE -> data.hasUnique(player, "shivering_tithe");
            case GRIM_HARVEST -> canStartObjective(data, player, "grim_harvest");
            case SAND_LITURGY -> canStartObjective(data, player, "sand_liturgy");
            case OSSUARY_VOW -> canStartObjective(data, player, "ossuary_vow");
            case COLD_LEDGER -> data.uniqueInt(player, "cold_ledger") <= 0;
            case HOLLOW_LANTERN -> !data.hasUnique(player, "hollow_lantern");
            case SNUFF_HOLLOW -> data.hasUnique(player, "hollow_lantern");
            case PALE_RATION -> data.uniqueInt(player, "pale_ration") <= 0;
            case WARD_OF_BONE -> !data.hasUnique(player, "bone_ward");
            case BREAK_BONE_WARD -> data.hasUnique(player, "bone_ward");
            case SILENT_DIVIDEND -> data.uniqueInt(player, "silent_dividend") <= 0;
            case DROWNED_NAME -> !data.hasUnique(player, "drowned_name");
            case BLACK_REPRIEVE -> !data.hasUnique(player, "black_reprieve");
            case HOUSE_OF_ASH -> !data.hasUnique(player, "house_of_ash");
            case BLOODWELL_REFLEX, LONG_HAND, HARVEST_SHARE, BALLISTIC_SCRIPT, FAR_LEDGER, CROWD_INTEREST, IRON_PULSE
                    -> ProgressionCardEffects.available(data, player, this);
            case PINHOLE_DOCTRINE, POINT_BLANK_RECEIPT, HIGH_ARC_CLAUSE, UPWARD_INTEREST, FIRST_VOLLEY, LAST_BOLT,
                    SOLITARY_MARK, RUNNING_SIGHT, STILL_HAND, PIERCED_CROWD, RED_KNUCKLE, OPENING_CUT,
                    EXECUTIONERS_MARGIN, LONE_DUEL, PRESSED_BLADE, HIGH_GROUND, LOW_ROAD, RUNNING_HAND,
                    STILL_POINT, SECOND_WOUND, ASH_PLATE, LAST_PLATE, ARROW_LEDGER, CLOSE_SEAL,
                    CROWD_SHELTER, SOLITARY_WARD, FALLING_INK, FIRE_MARGIN, BLAST_RECEIPT, NAME_WITHOUT_RECOIL,
                    LONG_STRIDE, SPRINT_CLAUSE, EMPTY_ROAD, HUNTED_ROAD, OPEN_SKY_FOOTNOTE, DEEP_ROAD,
                    FEATHERED_DEBT, CLIMBERS_MARGIN, LONGER_HAND, DUELISTS_REACH, STONE_DIVIDEND, TIMBER_SHARE,
                    MASON_TITHE, FORTUNE_MARGIN, SWIFT_TOOL, HARVEST_MEMORY, GRAVE_DIVIDEND, SCAVENGERS_NAME,
                    CANDLEWORK, QUIET_LEDGER
                    -> ExpandedProgressionCardEffects.available(data, player, this);
            case WRONG_DOOR, TEN_SECONDS_UNWRITTEN, VILLAGE_COLD_SHOULDER, IRON_ACCUSATION,
                    WARDENS_BLIND_SPOT, EMPTY_DEEP, ZOMBIE_ARMISTICE, BONE_TRUCE, CREEPER_COURTESY,
                    SPIDER_TREATY, ENDER_AMNESTY, WITCHS_PRIVILEGE, HOSTILE_CENSUS, FIRE_WITHOUT_FUEL,
                    WRONG_GRAVITY, HUNDRED_STEPS, RED_MINUTE, GLASS_SAINT, SHARED_PAIN, MEASURED_MERCY,
                    TIDAL_BODY, CHORUS_ERROR, RETURN_ADDRESS, LANTERN_BREAK, WHITE_NOISE, BLOOD_MONEY,
                    COIN_EDGE, CHANCE_ENGINE, BLACKOUT, AIR_BORROWED, BURNING_PACT, FROSTED_BLOOD,
                    NIGHT_PASS, DAY_PASS, HOLLOW_SKIN, GRAVE_SILENCE, BEAST_MARCH, GOLEM_ESCORT,
                    GOLEM_ENMITY, VILLAGE_EXILE, VILLAGE_PARDON, CREEPER_BLESSING, FALLING_CROWN,
                    STONE_SKIN, OPEN_SKY, DEEP_BREATH, FOURTH_CARD, FIFTH_CARD, NARROW_HAND,
                    CURSE_DAMPER, CURSE_BAIT, RARE_INK, CLEAN_MARGIN, BLACK_MARGIN, NO_ECHOES,
                    REMEDY_WITNESS, DEALERS_FOURTH, GOLDEN_CUT, DEEP_SHELF, CURSE_LULL,
                    REGISTRY_LOTTERY, EGG_WITH_NO_SHELL, SIXTY_FOURTH_STEP, STORM_RECEIPT,
                    BORROWED_FACE, MOB_EXCHANGE, BAD_RECEIPT, LUCKY_POCKET, STRANGE_INVITATION,
                    CERTAINLY_NOTHING, FOURTH_SHAPE, WHITE_THREAD, SCRIBBLED_RESPITE, PALE_MARGIN -> WildCardEffects.available(data, player, this);
            case QUICKENED_PULSE, STONE_BREATH, SHARPENED_HOUR, CLEAR_EYES, FEATHER_RECEIPT, DIVERS_MARGIN, CINDER_RECEIPT, MINERS_CREDIT, HUNTERS_REBATE, PALE_BANDAGE,
                    BLACK_BREAD, FIRST_CUT_DOUBLED, FIRST_ARROW_DOUBLED, THIN_AIR, STANDING_ORDER, RUNNING_ORDER, OPEN_SKY_LEDGER, DEEP_INK, RED_HARVEST, GRAY_HARVEST,
                    SCAVENGER_CLAUSE, MASONS_LUCK, WOODSMANS_SHARE, FARMERS_MARGIN, IRON_STEP, GLASS_STEP, WARD_LANTERN, QUIET_MOUTH, LOUD_NAME, SECOND_WIND,
                    INKED_APPETITE, CHARCOAL_TONGUE, FROSTED_EDGE, TETHERED_BLOOD, CUT_TETHER, ASHEN_LUNGS, CLEAR_THE_LUNGS, HOLLOW_BONES, FILL_THE_BONES, CLOCKWORK_NERVE,
                    STILL_THE_NERVE, BLOODLESS_VICTORY, WOUNDED_PROFIT, PATIENT_BLADE, MOVING_TARGET, CROWDED_LEDGER, SOLITARY_LEDGER, GOLDEN_HUNGER, PAY_THE_HUNGER, ASH_CROWN,
                    QUENCH_THE_CROWN, LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON, DEEP_SAINT, BLACK_PARDON, NINTH_LIFE, THE_LONG_NIGHT, GRAVE_WALKER
                    -> FreshCardEffects.available(data, player, this);
            case SWIFT_MERCY, IRON_WAKE, MOONWATER_DRAFT, FURNACE_VEIN, QUIET_STEP, RED_HOUR, PALE_STEP, HUNGRY_STEEL, WATCHERS_DRAFT, STONE_CHOIR,
                    RUNNING_DEBT, BLACK_CURRENT, HOLLOW_LIGHT, LAST_MATCH, GRAVE_SALT, ASH_DIVIDEND, FIRST_BLOOD, LAST_ARROW, LONG_BREATH, DEEP_STEP,
                    SKY_STEP, HUNTERS_REST, HUNTERS_RUSH, RED_LEDGER, BLACK_LEDGER, CANDLE_TAX, IRON_HARVEST, STONE_HARVEST, GLASS_RUNNER, HEAVY_HAND,
                    THIN_ARMOR, CORPSE_LANTERN, WOLFS_DEBT, EMPTY_ROOM, CROWDED_ROOM, RAIN_CLERK, SUN_CLERK, MOON_CLERK, BLOOD_CLOCK, CLEAN_HANDS,
                    COLD_IRON, WARM_IRON, PALE_SKIN, COLOR_RETURNED, SALT_LUNGS, FRESH_AIR, EMBER_MORTGAGE, PAY_THE_EMBER, GRAVE_CREDIT, EMPTY_PULSE
                    -> SecondWaveCardEffects.available(data, player, this);
            case KNEELING_FUSE, DROWN_THE_FUSE, THIRTEENTH_STEP, BREAK_THE_COUNT, STILLNESS_TAX, MOVE_THE_INK, BLACK_STATIC, SORT_THE_HAND, CHORUS_DEBT, CLOSE_THE_CHORUS,
                    LAST_FOOTPRINT, SECOND_GRAVITY, WITNESS_MARK, BLACKOUT_CLAUSE, BONE_MAGNET, BLOOD_CLOCK_HAND, STOLEN_COUNTENANCE, HOUSE_ALWAYS_WINS, RETURN_TO_SENDER, ASH_RECOIL,
                    AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, CROW_TOLL, PROJECTILE_AMNESTY, LOANED_MOMENT, DEAD_MANS_MARGIN, CROOKED_PARALLAX, BELL_WITHOUT_SOUND, GRAVE_INTEREST,
                    BLOOD_TELEGRAM, WOUND_EXCHANGE, STILL_POINT_BLACK, BLIND_AUCTION, CANCEL_AUCTION, LAST_WARDEN, FURNACE_HEART, KING_IN_RAGS, IRON_IDOL, LAST_CARD_DRAWN,
                    NULL_SPRINT, WORLD_OWES_NOTHING, RED_ECHO, PALE_RECOIL, FIFTH_TOLL, HOLLOW_CROWN, AFTERIMAGE_DEBT, DEBT_OF_DISTANCE, FINAL_AUCTION, UNWRITTEN_REMAINDER
                    -> ThirdWaveCardEffects.available(data, player, this);
            case THE_RED_PEN -> true;
            case DEBT_ECHO, WAGER_ECHO, SCAR_ECHO, REMEDY_ECHO, CONTRACT_ECHO, RITUAL_ECHO, COVENANT_ECHO, MASTER_ECHO, EPIC_ECHO, UNIQUE_ECHO, CURSE_ECHO, DEATH_ECHO
                    -> CardEchoSystem.available(data, player, this);
            case MIRROR_WRIT, BORROWED_ANATOMY, NINTH_MARGIN, CORPSE_LEDGER, CHAIN_OF_CUSTODY
                    -> FourthWaveCardEffects.available(data, player, this);
            case CLEAN_INTEREST, SECOND_ATTEMPT, THREE_CLEAN_LINES, HURRIED_OATH, WHITE_INK, BOUND_TESTIMONY, DOUBLE_MARGIN,
                    PERFECT_ORRERY, PERFECT_PROCESSION, BLACK_STUDY, CLOSE_THE_BOOK, ASHEN_REBUTTAL, SEVEN_PACES, STILL_WITNESS, HOLLOW_STEP,
                    RED_WAKE, IRON_AFTERTASTE, TIMBER_ECHO, RAIN_WRIT, CENSUS_OF_ONE, EMPTY_HAND_DOCTRINE, FULL_HAND_DOCTRINE, WOUND_CLOCK,
                    STOP_THE_CLOCK, HUNGER_OF_ORDER, BREAK_THE_ORDER, GRAVE_RECEIPT, FINAL_FOOTNOTE, MOON_ARCHIVE, SIXTH_WITNESS
                    -> FifthWaveCardEffects.available(data, player, this);
            case RAZOR_DIVIDEND, SPLINTERED_PLATE, LONGBOW_TESTAMENT, QUICKDRAW_CLAUSE, CRIMSON_ODDS, FROST_WIT, CINDER_WIT, BLOOD_RETURN, PALE_RESERVE, DEEP_MINERS_LEDGER, SCHOLARS_TITHE, DODGERS_INK, PLATEBREAKER_SCRIPT, SUNDERERS_NOTE, OPENING_FEE, HUNTERS_SCRIPT, BLOODLETTERS_MARGIN, WARMTH_AGAINST_STEEL, WINTER_AGAINST_BONE, SWIFT_PICK_CLAUSE, SILVER_LEDGER, SECOND_HEART_ACCOUNTING, DOUBLED_EDGE, IMPOSSIBLE_AIM,
                    RUSTED_EDGE, CLEAN_THE_RUST, SPLIT_NERVE, MEND_THE_NERVE, HOLLOW_MARROW, FILL_THE_MARROW, BROKEN_SIGHT, RESET_THE_SIGHT, ASHEN_PICK, WASH_THE_PICK, OPEN_WOUND_LEDGER, CLOSE_THE_WOUND,
                    RAZOR_DOCTRINE, SUNDERED_CREED, GOLDEN_BALLISTICS, SECOND_STRING, WINTER_TONGUE, CINDER_TONGUE, RED_CATECHISM, LEARNED_GRAVE, STONES_MEMORY, PERFECT_DEFLECTION, VEILED_PROTECTION, HUNGER_FOR_THE_LIVING,
                    RED_LAW, NO_ARMOR_IS_SACRED, ARROW_OF_LAST_ACCOUNT, FROZEN_VERDICT, BURNING_VERDICT, CRIMSON_USURY, HOUSE_TAKES_EXPERIENCE, EMPTY_PLATE_DOCTRINE, BONEBREAKER_COVENANT, BLOOD_IN_EXCESS, EXECUTIONER_OF_FULL_HEALTH, HOUSE_HAS_NUMBERS,
                    GLASS_RAZOR, BALLISTIC_PRAYER, BLACK_ICE_LEDGER, ASHEN_VOLLEY, SURGEONS_MARGIN, GHOST_STEP_LEDGER, PLUNDERED_LESSON, IRON_ALGEBRA, BREAKERS_INTEREST, RED_RESERVOIR, COLD_ACCOUNTING, CINDER_ACCOUNTING, HIGH_VELOCITY_CLAUSE, SNIPERS_DEBT, QUICK_HAND_TAX, PALE_REFLEX, QUARRY_FEVER, EDUCATED_VIOLENCE, MERCYS_INTEREST, HEMORRHAGE_CLAUSE, DEFLECTORS_WAGE, PICK_AND_BLADE, PREDATORS_ARITHMETIC, HOUSE_ACCELERANT, PAPER_SKIN, BIND_THE_SKIN, FROZEN_MARROW, WARM_THE_MARROW, CINDERED_NERVE, COOL_THE_NERVE, GREEDY_PICK, BREAK_THE_GREED, SERRATED_MEMORY, FILE_THE_MEMORY, HUNGRY_QUIVER, FEED_THE_QUIVER, BLACK_LANCET, MERCILESS_GEOMETRY, WINTER_ENGINE, CINDER_ENGINE, VAMPIRE_LEDGER, MASTER_QUARRY, ABSENT_TARGET, PERFECT_TRAJECTORY, PROFANED_AEGIS, RED_INSTRUCTION, PATIENT_HAND, HOUSE_COMPOUND, GLASS_SOVEREIGN, TEETH_BEHIND_STEEL, TERMINAL_BALLISTICS, WINTER_OWNS_BLOOD, ASH_OWNS_BREATH, RED_RESERVOIR_LAW, FINAL_QUARRY, UNTOUCHABLE_DEBT, SHREDDERS_CROWN, OPENING_EXECUTION, LAST_QUIVER, HOUSE_REWRITES_BODY,
                    KILLING_MOMENTUM, FIRST_CUT_CLAUSE, LAST_BREATH_MARGIN, STILL_BALLISTICS, MOVING_TARGET_CLAUSE, QUARRY_TEMPER, FURNACE_ARITHMETIC, WINTER_PULSE, EMPTY_STOMACH_DOCTRINE, FULL_STOMACH_DIVIDEND, MOONSHOT_RECEIPT, SUNBURN_LEDGER, DUELISTS_EXCEPTION, MOB_INTEREST, BROKEN_SHIELD_PREMIUM, HEAVY_POCKETS_CLAUSE, CLEAN_HANDS_CLAUSE, PLATED_DEBT, BLOODHOUND_CALCULUS, HUNTERS_RHYTHM, EXECUTIONER_CLOCK, REDIRECTION_LAW, GLASS_CANNON_ARCHIVE, QUARRY_COMMUNION, EMBER_DEBT_COLLECTOR, RIME_COLLECTOR, LONE_PREDATOR, CROWD_AUDITOR, FULL_QUIVER_LAW, VULTURES_MARGIN, GLASS_BLOOD, THICKEN_GLASS_BLOOD, LEAD_FINGERS, UNCLASP_FINGERS, WHITE_HOT_MARROW, QUENCH_WHITE_HOT_MARROW, DEAD_WINTER_NERVE, WAKE_WINTER_NERVE, AUDITORS_HUNGER, CLOSE_THE_AUDIT, HOLLOW_AIM, CORRECT_THE_AIM, BORROWED_SKIN, RETURN_THE_SKIN, RED_QUARRY, COOL_THE_QUARRY, REDLINE_ANATOMY, PERFECT_MURDER_GEOMETRY, METEOR_QUIVER, HOUSE_BLOOD_BANK, IMPOSSIBLE_WINTER, CREMATION_CLAUSE, QUARRY_OF_FLESH, LAST_ACCOUNT, CRITICAL_LOTTERY, ECHO_CHAMBER, HOUSE_FAVORITE_SEVEN, LAST_ARROW_IN_QUIVER, MERCY_AFTER_MURDER, REVOLVING_DOOR, ZOMBIE_ONE_V_ONE, FREE_DIAMOND, ONE_SKELETON, QUICK_MINING_JOB, SHORT_WALK, FREE_HEALING, ONE_BABY_ZOMBIE, LUCKY_SEVEN, NOTHING_HAPPENS, SMALL_REWARD
                    -> ApothicCardEffects.available(data, player, this);
            case ANOMALY_GILDED, ANOMALY_FERAL, ANOMALY_HOLLOW, ANOMALY_STATIC, ANOMALY_MIRROR, ANOMALY_BLACK
                    -> AnomalyCardSystem.available(data, player, this);
        };
    }

    private static boolean canStartObjective(LockData data, UUID player, String key) {
        if (data.hasUnique(player, "obj_" + key) || data.activeObjectiveCount(player) >= 3) return false;
        CardObjectives.Objective objective = CardObjectives.byKey(key);
        if (objective == null) return true;
        // One lane per objective family: a player may carry one Contract, one Ritual and
        // one Covenant at once, but cannot stack three overlapping kill contracts.
        return CardObjectives.activeCount(data, player, objective.kind) <= 0;
    }

    public String terms() {
        return String.format("loot %+.2f; %s", rewardAdd(), debtText.toLowerCase());
    }

    /** How a signed card stops affecting the player, written for the Grimoire detail page. */
    public String reliefText() {
        return switch (this) {
            case CRIMSON_BALANCE -> "Remove it with Reconciled Blood or Absolution in Black Ink.";
            case GLASS_NERVE -> "Remove it with Nerve Settled or Absolution in Black Ink.";
            case PALE_COVENANT -> "Remove it with Covenant Broken or Absolution in Black Ink.";
            case STILL_HEART -> "Remove it with Heart Awakened or Absolution in Black Ink.";
            case THIN_BLOOD -> "It ends automatically after its three +15% ward payments are spent; Thickened Blood or Absolution can end it early.";
            case BLOOD_TITHE -> "Heart Returned, Debt Unwritten or Absolution can remove the blood debt.";
            case FRAIL_HAND -> "Debt Unwritten or Absolution can remove the pilgrimage.";
            case ASHEN_TONGUE -> "It expires after ten minutes. Debt Unwritten or Absolution can remove it earlier.";
            case BELLGLASS_SIGHT -> "Shatter the Bellglass or Absolution removes the mark.";
            case THORN_LEDGER -> "Prune the Ledger or Absolution removes the mark.";
            case DUSTBOUND_SOLES -> "Cut the Dust Binding or Absolution removes the mark.";
            case LANTERN_BLOOD -> "Snuff the Lantern or Absolution removes the mark.";
            case HOLLOW_LANTERN -> "Extinguish the Hollow or Absolution removes the mark.";
            case WARD_OF_BONE -> "Break the Bone Ward or Absolution removes the mark.";
            case VILLAGE_EXILE -> "Village Pardon or Absolution ends the exile and its iron enforcement.";
            case SHIVERING_TITHE -> "Thaw the Tithe, Debt Unwritten or Absolution removes it.";
            case BORROWED_BREATH, IRON_DEBT, WATCHING_MARK -> "The debt is consumed by its stated trigger; Debt Unwritten can erase it first.";
            case LAST_CANDLE, LOADED_DICE, MERCYS_DUE, EMBER_COUNT, ORE_WHISPER, BORROWED_MOMENTUM,
                    HUNTERS_DIVIDEND, COAL_KISS, HEARTHMARK, SALT_CIRCLE, BLACK_COMPASS, IRON_ECHO,
                    GRAVE_RATION, POCKET_ECLIPSE, PILGRIMS_LUCK, VEIN_DRINKER, QUICKSILVER_PRAYER,
                    FERRYMAN_LEDGER, COLD_LEDGER, PALE_RATION, SILENT_DIVIDEND -> "This card ends when its timer or remaining charges are exhausted.";
            case BLOODWELL_REFLEX, LONG_HAND, HARVEST_SHARE, BALLISTIC_SCRIPT, FAR_LEDGER, CROWD_INTEREST, IRON_PULSE
                    -> "Stable and revised copies expire after their written timer. A Palimpsest copy is permanent and no longer returns to the deck.";
            case PINHOLE_DOCTRINE, POINT_BLANK_RECEIPT, HIGH_ARC_CLAUSE, UPWARD_INTEREST, FIRST_VOLLEY, LAST_BOLT,
                    SOLITARY_MARK, RUNNING_SIGHT, STILL_HAND, PIERCED_CROWD, RED_KNUCKLE, OPENING_CUT,
                    EXECUTIONERS_MARGIN, LONE_DUEL, PRESSED_BLADE, HIGH_GROUND, LOW_ROAD, RUNNING_HAND,
                    STILL_POINT, SECOND_WOUND, ASH_PLATE, LAST_PLATE, ARROW_LEDGER, CLOSE_SEAL,
                    CROWD_SHELTER, SOLITARY_WARD, FALLING_INK, FIRE_MARGIN, BLAST_RECEIPT, NAME_WITHOUT_RECOIL,
                    LONG_STRIDE, SPRINT_CLAUSE, EMPTY_ROAD, HUNTED_ROAD, OPEN_SKY_FOOTNOTE, DEEP_ROAD,
                    FEATHERED_DEBT, CLIMBERS_MARGIN, LONGER_HAND, DUELISTS_REACH, STONE_DIVIDEND, TIMBER_SHARE,
                    MASON_TITHE, FORTUNE_MARGIN, SWIFT_TOOL, HARVEST_MEMORY, GRAVE_DIVIDEND, SCAVENGERS_NAME,
                    CANDLEWORK, QUIET_LEDGER
                    -> "Stable and revised copies expire after their written timer. A Palimpsest copy is permanent and no longer returns to the deck.";
            case ROTTEN_LEDGER, BONE_TALLY, CREEPER_CLAUSE, SILK_WARRANT, ENDER_AUDIT, WITCH_LEDGER,
                    STONE_COMMUNION, VEIN_LITANY, WOODEN_CONFESSION, EARTHEN_VIGIL, OBSIDIAN_PRAYER,
                    RED_CENSUS, IRON_SILENCE, INNOCENCE_TAX, WITCHFIRE_TITHE, GRIM_HARVEST,
                    SAND_LITURGY, OSSUARY_VOW -> "The requirement ends when its objective is completed; payment is then applied automatically.";
            case CROOKED_PRIVATE, VEILED_PRIVATE, EXACTING_PRIVATE -> "Signing another Master's private law replaces this one.";
            case MEMENTO_MORI, BLACK_SUN, COFFIN_ROAD, GRAVE_BELL -> "This is a Death law. Ordinary remedies do not erase it.";
            case MOONLIT_HUNT, RED_MARCH, FIRST_SUPPER, LAST_WITNESS, CINDER_VOW,
                    DROWNED_NAME, HOUSE_OF_ASH, BLACK_DIVIDEND, SECOND_LEDGER, BLACK_REPRIEVE -> "This is a persistent world law; ordinary milk does not remove it.";
            case ABSOLUTION, RECONCILED_BLOOD, HEART_RETURNED, NERVE_SETTLED, COVENANT_BROKEN,
                    HEART_AWAKENED, DEBT_UNWRITTEN, THICKENED_BLOOD, SHATTER_BELLGLASS, PRUNE_THORNS,
                    CUT_DUST_BINDING, SNUFF_LANTERN, SNUFF_HOLLOW, BREAK_BONE_WARD, THAW_THE_TITHE,
                    VILLAGE_PARDON -> "This is a one-use remedy; its work is done when signed.";
            case SCRIBBLED_RESPITE -> "The clause spends itself after the next three ordinary wards have received their extra time.";
            case PALE_MARGIN -> "The +2 second minigame margin is permanent. Its original heart debt can be repaid separately, but repaying it does not erase the margin.";
            case QUICKENED_PULSE, STONE_BREATH, SHARPENED_HOUR, CLEAR_EYES, FEATHER_RECEIPT, DIVERS_MARGIN, CINDER_RECEIPT, MINERS_CREDIT, HUNTERS_REBATE, PALE_BANDAGE,
                    BLACK_BREAD, FIRST_CUT_DOUBLED, FIRST_ARROW_DOUBLED, THIN_AIR, STANDING_ORDER, RUNNING_ORDER, OPEN_SKY_LEDGER, DEEP_INK, RED_HARVEST, GRAY_HARVEST,
                    SCAVENGER_CLAUSE, MASONS_LUCK, WOODSMANS_SHARE, FARMERS_MARGIN, IRON_STEP, GLASS_STEP, WARD_LANTERN, QUIET_MOUTH, LOUD_NAME, SECOND_WIND,
                    INKED_APPETITE, CHARCOAL_TONGUE, FROSTED_EDGE, TETHERED_BLOOD, CUT_TETHER, ASHEN_LUNGS, CLEAR_THE_LUNGS, HOLLOW_BONES, FILL_THE_BONES, CLOCKWORK_NERVE,
                    STILL_THE_NERVE, BLOODLESS_VICTORY, WOUNDED_PROFIT, PATIENT_BLADE, MOVING_TARGET, CROWDED_LEDGER, SOLITARY_LEDGER, GOLDEN_HUNGER, PAY_THE_HUNGER, ASH_CROWN,
                    QUENCH_THE_CROWN, LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON, DEEP_SAINT, BLACK_PARDON, NINTH_LIFE, THE_LONG_NIGHT, GRAVE_WALKER
                    -> FreshCardEffects.reliefText(this);
            case SWIFT_MERCY, IRON_WAKE, MOONWATER_DRAFT, FURNACE_VEIN, QUIET_STEP, RED_HOUR, PALE_STEP, HUNGRY_STEEL, WATCHERS_DRAFT, STONE_CHOIR,
                    RUNNING_DEBT, BLACK_CURRENT, HOLLOW_LIGHT, LAST_MATCH, GRAVE_SALT, ASH_DIVIDEND, FIRST_BLOOD, LAST_ARROW, LONG_BREATH, DEEP_STEP,
                    SKY_STEP, HUNTERS_REST, HUNTERS_RUSH, RED_LEDGER, BLACK_LEDGER, CANDLE_TAX, IRON_HARVEST, STONE_HARVEST, GLASS_RUNNER, HEAVY_HAND,
                    THIN_ARMOR, CORPSE_LANTERN, WOLFS_DEBT, EMPTY_ROOM, CROWDED_ROOM, RAIN_CLERK, SUN_CLERK, MOON_CLERK, BLOOD_CLOCK, CLEAN_HANDS,
                    COLD_IRON, WARM_IRON, PALE_SKIN, COLOR_RETURNED, SALT_LUNGS, FRESH_AIR, EMBER_MORTGAGE, PAY_THE_EMBER, GRAVE_CREDIT, EMPTY_PULSE
                    -> SecondWaveCardEffects.reliefText(this);
            case KNEELING_FUSE, DROWN_THE_FUSE, THIRTEENTH_STEP, BREAK_THE_COUNT, STILLNESS_TAX, MOVE_THE_INK, BLACK_STATIC, SORT_THE_HAND, CHORUS_DEBT, CLOSE_THE_CHORUS,
                    LAST_FOOTPRINT, SECOND_GRAVITY, WITNESS_MARK, BLACKOUT_CLAUSE, BONE_MAGNET, BLOOD_CLOCK_HAND, STOLEN_COUNTENANCE, HOUSE_ALWAYS_WINS, RETURN_TO_SENDER, ASH_RECOIL,
                    AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, CROW_TOLL, PROJECTILE_AMNESTY, LOANED_MOMENT, DEAD_MANS_MARGIN, CROOKED_PARALLAX, BELL_WITHOUT_SOUND, GRAVE_INTEREST,
                    BLOOD_TELEGRAM, WOUND_EXCHANGE, STILL_POINT_BLACK, BLIND_AUCTION, CANCEL_AUCTION, LAST_WARDEN, FURNACE_HEART, KING_IN_RAGS, IRON_IDOL, LAST_CARD_DRAWN,
                    NULL_SPRINT, WORLD_OWES_NOTHING, RED_ECHO, PALE_RECOIL, FIFTH_TOLL, HOLLOW_CROWN, AFTERIMAGE_DEBT, DEBT_OF_DISTANCE, FINAL_AUCTION, UNWRITTEN_REMAINDER
                    -> ThirdWaveCardEffects.reliefText(this);
            case THE_RED_PEN -> "The Red Pen spends itself inside the current hand. It never enters the persistent law list.";
            case DEBT_ECHO, WAGER_ECHO, SCAR_ECHO, REMEDY_ECHO, CONTRACT_ECHO, RITUAL_ECHO, COVENANT_ECHO, MASTER_ECHO, EPIC_ECHO, UNIQUE_ECHO, CURSE_ECHO, DEATH_ECHO
                    -> "The primer waits until a non-Echo card of its written family is signed, then spends itself on that resolution.";
            case CLEAN_INTEREST, SECOND_ATTEMPT, THREE_CLEAN_LINES, HURRIED_OATH, WHITE_INK, BOUND_TESTIMONY, DOUBLE_MARGIN,
                    PERFECT_ORRERY, PERFECT_PROCESSION, BLACK_STUDY, CLOSE_THE_BOOK, ASHEN_REBUTTAL, SEVEN_PACES, STILL_WITNESS, HOLLOW_STEP,
                    RED_WAKE, IRON_AFTERTASTE, TIMBER_ECHO, RAIN_WRIT, CENSUS_OF_ONE, EMPTY_HAND_DOCTRINE, FULL_HAND_DOCTRINE, WOUND_CLOCK,
                    STOP_THE_CLOCK, HUNGER_OF_ORDER, BREAK_THE_ORDER, GRAVE_RECEIPT, FINAL_FOOTNOTE, MOON_ARCHIVE, SIXTH_WITNESS
                    -> FifthWaveCardEffects.reliefText(this);
            case MIRROR_WRIT, BORROWED_ANATOMY, NINTH_MARGIN, CHAIN_OF_CUSTODY
                    -> "This is a persistent world law; ordinary milk does not remove it.";
            case CORPSE_LEDGER -> "This is a Death law. Returning to a recorded corpse closes only the current corpse debt, not the law itself.";
            case REFRESH_HAND -> "This card only redraws the hand.";
            default -> ApothicCardEffects.isApothic(this) ? ApothicCardEffects.reliefText(this) : "The card ends according to the condition written in its terms.";
        };
    }

    public static ForbiddenBargain byId(int id) {
        for (ForbiddenBargain b : values()) if (b.id == id) return b;
        return null;
    }
}
