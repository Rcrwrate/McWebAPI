/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

describe("recipes", () => {
    it("gt maps", async () => {
        const r = await api.getGTRecipeMaps()
        assert.ok(r.length > 0)
        assert.ok(Joi.array().items(v.GTRecipeMapSchema).validate(r).error == undefined)
    })

    it("crafting all", async () => {
        const r = await api.getCraftingRecipes({ limit: 50 })
        assert.ok(v.CraftingRecipesResultSchema.validate(r).error == undefined)
        assert.ok(r.recipes.length > 0)
    })

    it("crafting by output", async () => {
        const r = await api.getCraftingRecipes({ type: "output", id: 1, damage: 0, limit: 10 })
        assert.ok(v.CraftingRecipesResultSchema.validate(r).error == undefined)
    })

    it("crafting by input", async () => {
        const r = await api.getCraftingRecipes({ type: "input", id: 339, limit: 10 })
        assert.ok(v.CraftingRecipesResultSchema.validate(r).error == undefined)
    })

    it("furnace all", async () => {
        const r = await api.getFurnaceRecipes({ limit: 50 })
        assert.ok(v.FurnaceRecipesResultSchema.validate(r).error == undefined)
        assert.ok(r.recipes.length > 0)
    })

    it("gt by map", async () => {
        const r = await api.getGTRecipes({ map: "gt.recipe.macerator", limit: 10 })
        assert.ok(v.GTRecipesResultSchema.validate(r).error == undefined)
        assert.ok(r.recipes.length > 0)
    })

    it("gt by item", async () => {
        const r = await api.getGTRecipes({ type: "output", id: 7437, damage: 2631, limit: 10 })
        assert.ok(v.GTRecipesResultSchema.validate(r).error == undefined)
    })

    it("gt by fluid", async () => {
        const r = await api.getGTRecipes({ type: "output", fluid: "water", limit: 10 })
        assert.ok(v.GTRecipesResultSchema.validate(r).error == undefined)
    })
})
