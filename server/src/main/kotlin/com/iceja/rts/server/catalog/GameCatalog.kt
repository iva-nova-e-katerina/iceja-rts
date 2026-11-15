package com.iceja.rts.server.catalog

object GameCatalog {
    const val CATALOG_VERSION: String = "2026.11.1"

    val ruleset: Ruleset = Ruleset()

    val chassis: List<ChassisDefinition> = listOf(
        ChassisDefinition(
            id = "GRUB",
            name = "Личинка",
            sizeClass = "S",
            massKg = 20,
            hp = 80,
            cost = 40,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 2),
                SocketCapacity(SocketType.FRONT, 2),
                SocketCapacity(SocketType.UNDER, 1)
            ),
            comfortableMassKg = 80,
            hardMassKg = 150,
            baseRadiusMm = 300
        ),
        ChassisDefinition(
            id = "CRAWLER",
            name = "Ползун",
            sizeClass = "S",
            massKg = 30,
            hp = 100,
            cost = 60,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 3),
                SocketCapacity(SocketType.FRONT, 2),
                SocketCapacity(SocketType.LEFT, 1),
                SocketCapacity(SocketType.RIGHT, 1),
                SocketCapacity(SocketType.TOP, 1)
            ),
            comfortableMassKg = 80,
            hardMassKg = 150,
            baseRadiusMm = 300
        ),
        ChassisDefinition(
            id = "BRUTE",
            name = "Громила",
            sizeClass = "M",
            massKg = 120,
            hp = 300,
            cost = 120,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 2),
                SocketCapacity(SocketType.FRONT, 2),
                SocketCapacity(SocketType.BACK, 1),
                SocketCapacity(SocketType.LEFT, 1),
                SocketCapacity(SocketType.RIGHT, 1),
                SocketCapacity(SocketType.TOP, 2),
                SocketCapacity(SocketType.UNDER, 1)
            ),
            comfortableMassKg = 300,
            hardMassKg = 700,
            baseRadiusMm = 500
        ),
        ChassisDefinition(
            id = "TITAN",
            name = "Титан",
            sizeClass = "L",
            massKg = 400,
            hp = 900,
            cost = 240,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 3),
                SocketCapacity(SocketType.FRONT, 2),
                SocketCapacity(SocketType.BACK, 2),
                SocketCapacity(SocketType.LEFT, 2),
                SocketCapacity(SocketType.RIGHT, 2),
                SocketCapacity(SocketType.TOP, 1)
            ),
            comfortableMassKg = 1_200,
            hardMassKg = 2_500,
            baseRadiusMm = 900
        ),
        ChassisDefinition(
            id = "FLYER",
            name = "Летун",
            sizeClass = "S",
            massKg = 40,
            hp = 120,
            cost = 100,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 2),
                SocketCapacity(SocketType.FRONT, 1),
                SocketCapacity(SocketType.LEFT, 1),
                SocketCapacity(SocketType.RIGHT, 1),
                SocketCapacity(SocketType.TOP, 2),
                SocketCapacity(SocketType.UNDER, 1)
            ),
            comfortableMassKg = 80,
            hardMassKg = 150,
            baseRadiusMm = 350,
            canFly = true
        ),
        ChassisDefinition(
            id = "BURROWER",
            name = "Копатель",
            sizeClass = "M",
            massKg = 150,
            hp = 350,
            cost = 140,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 2),
                SocketCapacity(SocketType.FRONT, 1),
                SocketCapacity(SocketType.LEFT, 1),
                SocketCapacity(SocketType.RIGHT, 1),
                SocketCapacity(SocketType.TOP, 1),
                SocketCapacity(SocketType.UNDER, 2)
            ),
            comfortableMassKg = 300,
            hardMassKg = 700,
            baseRadiusMm = 500,
            canBurrow = true
        ),
        ChassisDefinition(
            id = "STATIC",
            name = "Статик",
            sizeClass = "M",
            massKg = 200,
            hp = 600,
            cost = 160,
            sockets = listOf(
                SocketCapacity(SocketType.CORE, 2),
                SocketCapacity(SocketType.FRONT, 1),
                SocketCapacity(SocketType.TOP, 1)
            ),
            comfortableMassKg = 300,
            hardMassKg = 700,
            baseRadiusMm = 600,
            isStatic = true
        )
    )

    val parts: List<PartDefinition> = listOf(
        PartDefinition(
            id = "GANGLION",
            name = "Ганглий",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 20,
            massKg = 2,
            hpBonus = 10,
            energyBonus = 50,
            regenPerSecond = 3,
            controlBonus = 20,
            flags = setOf(PartFlag.BRAIN),
            description = "Минимальный мозг"
        ),
        PartDefinition(
            id = "CEREBRAL",
            name = "Церебральный узел",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 60,
            massKg = 5,
            hpBonus = 30,
            energyBonus = 100,
            regenPerSecond = 5,
            controlBonus = 40,
            flags = setOf(PartFlag.BRAIN),
            description = "Средний мозг"
        ),
        PartDefinition(
            id = "HIVEMIND",
            name = "Коллективный разум",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 120,
            massKg = 10,
            hpBonus = 50,
            energyBonus = 200,
            regenPerSecond = 8,
            controlBonus = 80,
            flags = setOf(PartFlag.BRAIN),
            description = "Много поведения"
        ),
        PartDefinition(
            id = "HEART",
            name = "Сердце",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 25,
            massKg = 5,
            hpBonus = 20,
            energyBonus = 100,
            regenPerSecond = 5,
            flags = setOf(PartFlag.HEART),
            description = "Базовый насос"
        ),
        PartDefinition(
            id = "METABOLIC",
            name = "Метаболический реактор",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 80,
            massKg = 20,
            hpBonus = 40,
            energyBonus = 300,
            regenPerSecond = 15,
            flags = setOf(PartFlag.METABOLIC),
            description = "Много энергии"
        ),
        PartDefinition(
            id = "HEMOPUMP",
            name = "Гемолимфный насос",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 40,
            massKg = 8,
            hpBonus = 50,
            energyBonus = 50,
            regenPerSecond = 2,
            flags = setOf(PartFlag.HEART),
            description = "Живучесть"
        ),
        PartDefinition(
            id = "NEURALTRUNK",
            name = "Нервный ствол",
            category = PartCategory.CORE,
            socket = SocketType.CORE,
            cost = 30,
            massKg = 3,
            hpBonus = 10,
            controlBonus = 10,
            description = "Связность"
        ),
        PartDefinition(
            id = "SCUTTLER",
            name = "Ноги-ходули",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.LEFT,
            cost = 30,
            massKg = 8,
            hpBonus = 10,
            traction = 80,
            energyDrainMilliPerSecond = 500,
            description = "Базовая локомоция"
        ),
        PartDefinition(
            id = "CLAWED",
            name = "Когтистые лапы",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.LEFT,
            cost = 45,
            massKg = 12,
            hpBonus = 15,
            traction = 120,
            evasionBonus = 10,
            energyDrainMilliPerSecond = 800,
            description = "Уклонение"
        ),
        PartDefinition(
            id = "SPRINT",
            name = "Беговые ноги",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.LEFT,
            cost = 60,
            massKg = 10,
            hpBonus = 10,
            traction = 150,
            speedMultiplierPct = 115,
            energyDrainMilliPerSecond = 1_500,
            description = "Высокая скорость"
        ),
        PartDefinition(
            id = "PADDLE",
            name = "Плавники",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.LEFT,
            cost = 35,
            massKg = 9,
            hpBonus = 10,
            traction = 90,
            energyDrainMilliPerSecond = 600,
            description = "Бонус в воде"
        ),
        PartDefinition(
            id = "WINGS",
            name = "Крылья",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.TOP,
            cost = 70,
            massKg = 8,
            hpBonus = 10,
            traction = 100,
            energyDrainMilliPerSecond = 2_000,
            flags = setOf(PartFlag.FLIGHT),
            description = "Полёт"
        ),
        PartDefinition(
            id = "BURROWCLAW",
            name = "Копательные когти",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.UNDER,
            cost = 50,
            massKg = 14,
            hpBonus = 15,
            traction = 60,
            energyDrainMilliPerSecond = 1_000,
            flags = setOf(PartFlag.BURROW),
            description = "Копание"
        ),
        PartDefinition(
            id = "SLUGFOOT",
            name = "Слизистая нога",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.UNDER,
            cost = 20,
            massKg = 15,
            hpBonus = 20,
            traction = 50,
            energyDrainMilliPerSecond = 400,
            description = "Дешёвая, медленная"
        ),
        PartDefinition(
            id = "SPIDERLEGS",
            name = "Паучьи ноги",
            category = PartCategory.LOCOMOTION,
            socket = SocketType.LEFT,
            cost = 55,
            massKg = 11,
            hpBonus = 10,
            traction = 130,
            turnMultiplierPct = 110,
            energyDrainMilliPerSecond = 900,
            description = "Быстрый поворот"
        ),
        PartDefinition(
            id = "FACET",
            name = "Фасеточные глаза",
            category = PartCategory.SENSOR,
            socket = SocketType.FRONT,
            cost = 15,
            massKg = 1,
            visionRangeMm = 12_000,
            visionAngleDeg = 120,
            description = "Базовый обзор"
        ),
        PartDefinition(
            id = "COMPOUND",
            name = "Сложные глаза",
            category = PartCategory.SENSOR,
            socket = SocketType.FRONT,
            cost = 30,
            massKg = 2,
            visionRangeMm = 18_000,
            visionAngleDeg = 180,
            description = "Лучший обзор"
        ),
        PartDefinition(
            id = "THERMAL",
            name = "Термолокатор",
            category = PartCategory.SENSOR,
            socket = SocketType.FRONT,
            cost = 25,
            massKg = 2,
            visionRangeMm = 15_000,
            visionAngleDeg = 90,
            description = "Видит сквозь дым"
        ),
        PartDefinition(
            id = "CHEMO",
            name = "Хеморецепторы",
            category = PartCategory.SENSOR,
            socket = SocketType.FRONT,
            cost = 20,
            massKg = 1,
            visionRangeMm = 20_000,
            visionAngleDeg = 360,
            description = "Запах"
        ),
        PartDefinition(
            id = "VIBRO",
            name = "Вибрационные сенсоры",
            category = PartCategory.SENSOR,
            socket = SocketType.UNDER,
            cost = 20,
            massKg = 2,
            visionRangeMm = 25_000,
            visionAngleDeg = 360,
            description = "Слух"
        ),
        PartDefinition(
            id = "ECHO",
            name = "Эхолокатор",
            category = PartCategory.SENSOR,
            socket = SocketType.FRONT,
            cost = 40,
            massKg = 3,
            visionRangeMm = 20_000,
            visionAngleDeg = 360,
            description = "Видит за укрытиями"
        ),
        PartDefinition(
            id = "TARGETING",
            name = "Прицельный ганглий",
            category = PartCategory.SENSOR,
            socket = SocketType.TOP,
            cost = 35,
            massKg = 2,
            accuracyBonus = 10,
            description = "Точность"
        ),
        PartDefinition(
            id = "MANDIBLE",
            name = "Жвалы",
            category = PartCategory.MELEE,
            socket = SocketType.FRONT,
            cost = 20,
            massKg = 2,
            weapon = WeaponProfile(
                damage = 12,
                cooldownTicks = 36,
                rangeMm = 800,
                damageType = DamageType.KINETIC,
                accuracy = 90
            ),
            description = "Дешёвое оружие"
        ),
        PartDefinition(
            id = "CLAWS",
            name = "Когти",
            category = PartCategory.MELEE,
            socket = SocketType.LEFT,
            cost = 30,
            massKg = 3,
            weapon = WeaponProfile(
                damage = 18,
                cooldownTicks = 30,
                rangeMm = 1_000,
                damageType = DamageType.PIERCING,
                accuracy = 90
            ),
            description = "Быстрое оружие"
        ),
        PartDefinition(
            id = "SPIKETAIL",
            name = "Шипастый хвост",
            category = PartCategory.MELEE,
            socket = SocketType.BACK,
            cost = 40,
            massKg = 5,
            weapon = WeaponProfile(
                damage = 30,
                cooldownTicks = 60,
                rangeMm = 1_500,
                damageType = DamageType.KINETIC,
                accuracy = 85,
                complexity = 2
            ),
            description = "Задевает нескольких"
        ),
        PartDefinition(
            id = "BONEHAMMER",
            name = "Костяной молот",
            category = PartCategory.MELEE,
            socket = SocketType.BACK,
            cost = 55,
            massKg = 10,
            weapon = WeaponProfile(
                damage = 45,
                cooldownTicks = 75,
                rangeMm = 1_200,
                damageType = DamageType.KINETIC,
                accuracy = 80,
                complexity = 2
            ),
            description = "Тяжёлый удар"
        ),
        PartDefinition(
            id = "ACIDGLAND",
            name = "Кислотные железы",
            category = PartCategory.MELEE,
            socket = SocketType.FRONT,
            cost = 50,
            massKg = 4,
            weapon = WeaponProfile(
                damage = 10,
                cooldownTicks = 45,
                rangeMm = 1_000,
                damageType = DamageType.ACID,
                accuracy = 85,
                dotDamagePerSecond = 10,
                dotDurationTicks = 90,
                complexity = 2
            ),
            description = "Коррозия"
        ),
        PartDefinition(
            id = "VENOMFANG",
            name = "Ядовитые клыки",
            category = PartCategory.MELEE,
            socket = SocketType.FRONT,
            cost = 45,
            massKg = 3,
            weapon = WeaponProfile(
                damage = 8,
                cooldownTicks = 36,
                rangeMm = 700,
                damageType = DamageType.TOXIC,
                accuracy = 90,
                dotDamagePerSecond = 6,
                dotDurationTicks = 120,
                complexity = 2
            ),
            description = "Яд"
        ),
        PartDefinition(
            id = "ELECTRO",
            name = "Электроорган",
            category = PartCategory.MELEE,
            socket = SocketType.BACK,
            cost = 60,
            massKg = 6,
            weapon = WeaponProfile(
                damage = 20,
                cooldownTicks = 60,
                rangeMm = 1_500,
                damageType = DamageType.ELECTRIC,
                accuracy = 85,
                complexity = 2
            ),
            description = "Электрический разряд"
        ),
        PartDefinition(
            id = "SPINE",
            name = "Спикулы",
            category = PartCategory.RANGED,
            socket = SocketType.TOP,
            cost = 40,
            massKg = 4,
            weapon = WeaponProfile(
                damage = 14,
                cooldownTicks = 45,
                rangeMm = 15_000,
                damageType = DamageType.PIERCING,
                accuracy = 85,
                projectileSpeedMmPerTick = 667,
                complexity = 2,
                ranged = true
            ),
            description = "Прямой выстрел"
        ),
        PartDefinition(
            id = "ACIDSPIT",
            name = "Кислотный плеватель",
            category = PartCategory.RANGED,
            socket = SocketType.FRONT,
            cost = 55,
            massKg = 5,
            weapon = WeaponProfile(
                damage = 10,
                cooldownTicks = 54,
                rangeMm = 12_000,
                damageType = DamageType.ACID,
                accuracy = 80,
                projectileSpeedMmPerTick = 400,
                splashRadiusMm = 1_000,
                dotDamagePerSecond = 8,
                dotDurationTicks = 60,
                complexity = 2,
                ranged = true
            ),
            description = "Кислотная лужа"
        ),
        PartDefinition(
            id = "SPOREMORTAR",
            name = "Споровый миномёт",
            category = PartCategory.RANGED,
            socket = SocketType.TOP,
            cost = 70,
            massKg = 8,
            weapon = WeaponProfile(
                damage = 25,
                cooldownTicks = 90,
                rangeMm = 20_000,
                damageType = DamageType.TOXIC,
                accuracy = 70,
                projectileSpeedMmPerTick = 500,
                splashRadiusMm = 2_000,
                complexity = 3,
                ranged = true
            ),
            description = "Навесной AoE"
        ),
        PartDefinition(
            id = "ELECTROARC",
            name = "Электрическая дуга",
            category = PartCategory.RANGED,
            socket = SocketType.BACK,
            cost = 80,
            massKg = 6,
            weapon = WeaponProfile(
                damage = 18,
                cooldownTicks = 75,
                rangeMm = 10_000,
                damageType = DamageType.ELECTRIC,
                accuracy = 85,
                projectileSpeedMmPerTick = 1_000,
                complexity = 3,
                ranged = true
            ),
            description = "Цепь из двух целей"
        ),
        PartDefinition(
            id = "HARPOON",
            name = "Гарпун",
            category = PartCategory.RANGED,
            socket = SocketType.FRONT,
            cost = 45,
            massKg = 5,
            weapon = WeaponProfile(
                damage = 20,
                cooldownTicks = 60,
                rangeMm = 8_000,
                damageType = DamageType.PIERCING,
                accuracy = 85,
                projectileSpeedMmPerTick = 833,
                complexity = 2,
                ranged = true
            ),
            description = "Притягивает цель"
        ),
        PartDefinition(
            id = "CHITIN",
            name = "Хитиновая пластина",
            category = PartCategory.DEFENSE,
            socket = SocketType.TOP,
            cost = 25,
            massKg = 10,
            hpBonus = 20,
            armor = ArmorProfile(kinetic = 15),
            description = "Дешёвая броня"
        ),
        PartDefinition(
            id = "BONEPLATE",
            name = "Костяные пластины",
            category = PartCategory.DEFENSE,
            socket = SocketType.TOP,
            cost = 40,
            massKg = 15,
            hpBonus = 40,
            armor = ArmorProfile(kinetic = 25, piercing = 10),
            description = "Тяжёлая броня"
        ),
        PartDefinition(
            id = "REGEN",
            name = "Регенерационная железа",
            category = PartCategory.DEFENSE,
            socket = SocketType.CORE,
            cost = 50,
            massKg = 6,
            hpBonus = 30,
            regenPercentPerSecond = 1,
            description = "Регенерация"
        ),
        PartDefinition(
            id = "MUCUS",
            name = "Слизистый покров",
            category = PartCategory.DEFENSE,
            socket = SocketType.UNDER,
            cost = 30,
            massKg = 5,
            hpBonus = 10,
            evasionBonus = 10,
            speedMultiplierPct = 95,
            description = "Уклонение"
        ),
        PartDefinition(
            id = "CAMO",
            name = "Камуфляжная кожа",
            category = PartCategory.DEFENSE,
            socket = SocketType.TOP,
            cost = 45,
            massKg = 4,
            hpBonus = 10,
            flags = setOf(PartFlag.CAMOUFLAGE),
            description = "Скрытность"
        ),
        PartDefinition(
            id = "ANTITOXIN",
            name = "Антитоксиновая железа",
            category = PartCategory.DEFENSE,
            socket = SocketType.CORE,
            cost = 30,
            massKg = 3,
            hpBonus = 10,
            armor = ArmorProfile(toxic = 20),
            description = "Сопротивление яду"
        ),
        PartDefinition(
            id = "THERMOVENT",
            name = "Термовенты",
            category = PartCategory.DEFENSE,
            socket = SocketType.TOP,
            cost = 35,
            massKg = 5,
            hpBonus = 10,
            description = "Охлаждение"
        ),
        PartDefinition(
            id = "SLIMEARMOR",
            name = "Слизистая броня",
            category = PartCategory.DEFENSE,
            socket = SocketType.UNDER,
            cost = 35,
            massKg = 8,
            hpBonus = 20,
            armor = ArmorProfile(acid = 10),
            description = "Сопротивление кислоте"
        ),
        PartDefinition(
            id = "NECTARCROP",
            name = "Нектарный зоб",
            category = PartCategory.UTILITY,
            socket = SocketType.CORE,
            cost = 30,
            massKg = 5,
            flags = setOf(PartFlag.NECTAR_CROP),
            description = "Сбор нектара"
        ),
        PartDefinition(
            id = "PHEROMONE",
            name = "Феромонные железы",
            category = PartCategory.UTILITY,
            socket = SocketType.BACK,
            cost = 25,
            massKg = 2,
            flags = setOf(PartFlag.PHEROMONE),
            description = "Усиление союзников"
        ),
        PartDefinition(
            id = "OVIPOSITOR",
            name = "Яйцеклад",
            category = PartCategory.UTILITY,
            socket = SocketType.UNDER,
            cost = 60,
            massKg = 8,
            flags = setOf(PartFlag.OVIPOSITOR),
            description = "Размножение"
        ),
        PartDefinition(
            id = "SYMBIOTE",
            name = "Симбионт",
            category = PartCategory.UTILITY,
            socket = SocketType.CORE,
            cost = 50,
            massKg = 4,
            flags = setOf(PartFlag.SYMBIOTE),
            description = "Лечение союзников"
        ),
        PartDefinition(
            id = "BURROWGLAND",
            name = "Копательная железа",
            category = PartCategory.UTILITY,
            socket = SocketType.UNDER,
            cost = 40,
            massKg = 6,
            flags = setOf(PartFlag.BURROW),
            description = "Копание"
        ),
        PartDefinition(
            id = "ADRENAL",
            name = "Адренальная железа",
            category = PartCategory.UTILITY,
            socket = SocketType.CORE,
            cost = 35,
            massKg = 3,
            flags = setOf(PartFlag.ADRENAL),
            description = "Ярость при низком HP"
        ),
        PartDefinition(
            id = "SCAVENGER",
            name = "Падальщик",
            category = PartCategory.UTILITY,
            socket = SocketType.FRONT,
            cost = 30,
            massKg = 4,
            flags = setOf(PartFlag.SCAVENGER),
            description = "Ест трупы"
        ),
        PartDefinition(
            id = "STICKY",
            name = "Липкие ступни",
            category = PartCategory.UTILITY,
            socket = SocketType.UNDER,
            cost = 20,
            massKg = 3,
            flags = setOf(PartFlag.STICKY),
            description = "Игнорирует замедление"
        ),
        PartDefinition(
            id = "NEUROLINK",
            name = "Нейросвязь",
            category = PartCategory.UTILITY,
            socket = SocketType.CORE,
            cost = 45,
            massKg = 3,
            flags = setOf(PartFlag.NEUROLINK),
            description = "Делит обзор"
        ),
        PartDefinition(
            id = "LURE",
            name = "Биолюминесцентная приманка",
            category = PartCategory.UTILITY,
            socket = SocketType.TOP,
            cost = 25,
            massKg = 2,
            flags = setOf(PartFlag.LURE),
            description = "Привлекает нейтральных существ"
        )
    )

    val behaviorPresets: List<BehaviorPresetDefinition> = listOf(
        BehaviorPresetDefinition(
            id = "BERSERK",
            name = "Берсерк",
            cp = 8,
            description = "Атаковать ближайшего, не отступать",
            aggression = 90,
            caution = 10,
            cohesion = 20,
            greed = 10,
            loyalty = 30,
            focus = 80,
            adaptability = 30
        ),
        BehaviorPresetDefinition(
            id = "GUARD",
            name = "Страж",
            cp = 7,
            description = "Держать точку, атаковать в радиусе",
            aggression = 40,
            caution = 50,
            cohesion = 40,
            greed = 10,
            loyalty = 90,
            focus = 60,
            adaptability = 30
        ),
        BehaviorPresetDefinition(
            id = "PACK",
            name = "Стайный охотник",
            cp = 10,
            description = "Держаться группы, окружать цель",
            aggression = 60,
            caution = 40,
            cohesion = 90,
            greed = 20,
            loyalty = 40,
            focus = 70,
            adaptability = 50
        ),
        BehaviorPresetDefinition(
            id = "SKIRMISHER",
            name = "Застрельщик",
            cp = 9,
            description = "Держать дистанцию, отступать при сближении",
            aggression = 50,
            caution = 60,
            cohesion = 30,
            greed = 20,
            loyalty = 20,
            focus = 60,
            adaptability = 70
        ),
        BehaviorPresetDefinition(
            id = "SCAVENGER",
            name = "Падальщик",
            cp = 8,
            description = "Избегать боя, есть трупы, собирать нектар",
            aggression = 20,
            caution = 70,
            cohesion = 30,
            greed = 90,
            loyalty = 20,
            focus = 20,
            adaptability = 60
        ),
        BehaviorPresetDefinition(
            id = "SIEGE",
            name = "Осадный червь",
            cp = 11,
            description = "Атаковать улей, игнорировать мелкие цели",
            aggression = 70,
            caution = 20,
            cohesion = 20,
            greed = 10,
            loyalty = 10,
            focus = 100,
            adaptability = 10
        ),
        BehaviorPresetDefinition(
            id = "DEFENDER",
            name = "Защитник",
            cp = 8,
            description = "Защищать улей, перехватывать угрозы",
            aggression = 30,
            caution = 60,
            cohesion = 50,
            greed = 10,
            loyalty = 95,
            focus = 70,
            adaptability = 30
        ),
        BehaviorPresetDefinition(
            id = "FLANKER",
            name = "Фланкер",
            cp = 10,
            description = "Обходить с фланга, атаковать дальних",
            aggression = 70,
            caution = 40,
            cohesion = 60,
            greed = 10,
            loyalty = 30,
            focus = 50,
            adaptability = 90
        )
    )

    val maps: List<MapDefinition> = listOf(
        MapDefinition(
            id = "duel-arena",
            name = "Арена дуэли"
        ),
        MapDefinition(
            id = "pillars",
            name = "Колонны",
            obstacles = listOf(
                Obstacle(38_000, 38_000, 8_000, 8_000),
                Obstacle(82_000, 82_000, 8_000, 8_000),
                Obstacle(38_000, 82_000, 8_000, 8_000),
                Obstacle(82_000, 38_000, 8_000, 8_000),
                Obstacle(60_000, 60_000, 8_000, 8_000)
            )
        ),
        MapDefinition(
            id = "acid-marsh",
            name = "Кислотное болото",
            obstacles = listOf(
                Obstacle(30_000, 50_000, 10_000, 6_000),
                Obstacle(88_000, 72_000, 10_000, 6_000),
                Obstacle(54_000, 20_000, 6_000, 10_000),
                Obstacle(68_000, 98_000, 6_000, 10_000)
            )
        )
    )

    val partsById: Map<String, PartDefinition> = parts.associateBy { it.id }
    val chassisById: Map<String, ChassisDefinition> = chassis.associateBy { it.id }
    val mapsById: Map<String, MapDefinition> = maps.associateBy { it.id }
    val presetsById: Map<String, BehaviorPresetDefinition> = behaviorPresets.associateBy { it.id }

    fun part(partId: String): PartDefinition =
        partsById[partId] ?: throw IllegalArgumentException("Unknown part: $partId")

    fun chassis(chassisId: String): ChassisDefinition =
        chassisById[chassisId] ?: throw IllegalArgumentException("Unknown chassis: $chassisId")

    fun map(mapId: String): MapDefinition =
        mapsById[mapId] ?: throw IllegalArgumentException("Unknown map: $mapId")

    fun preset(presetId: String): BehaviorPresetDefinition =
        presetsById[presetId] ?: throw IllegalArgumentException("Unknown behavior preset: $presetId")
}
