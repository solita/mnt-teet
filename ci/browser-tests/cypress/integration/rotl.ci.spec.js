// Road Object Type Library
context("ROTL", () => {
    beforeEach(() => {
        cy.dummyLogin("Carla")
        cy.visit("#/asset/type-library")
    })

    it("renders list of feature groups that can be opened", () => {
        cy.get("[data-ident=':fgroup/structures'] .MuiCardHeader-action button").click()

        cy.get("[data-ident=':fclass/bridge'] .MuiCardHeader-action button").click()

        cy.get("[data-ident=':ctype/bridgespan'] .MuiCardHeader-action button").click()
        cy.get("td").contains(":bridgespan/spannumber")

    })
})
