// Road Object Type Library
context("ROTL", () => {
    beforeEach(() => {
        cy.dummyLogin("Carla")
        cy.visit("#/asset/type-library")
    })

    it("renders list of feature groups that can be opened", () => {

        // open fgroups
        cy.get("[data-ident=':fgroup/fgroups'] button.MuiIconButton-root").first().click()

        // open structures fgroup
        cy.get("[data-ident=':fgroup/structures'] button.MuiIconButton-root").first().click()

        // open bridge
        cy.get("[data-ident=':fclass/bridge'] button.MuiIconButton-root").first().click()

        // click to select bridge span
        cy.get("[data-ident=':ctype/bridgespan'] a").first().click()

        // details view has bridge attribute listed
        cy.get("td").contains(":bridgespan/spannumber")

    })

    it("renders list of materials with links to relevant feature groups", () => {

        // open materials
        cy.get("[data-ident=':material/materials'] button.MuiIconButton-root").first().click()

        // open aluminum
        cy.get("[data-ident=':material/aluminium'] a").first().click()

        // open link to Structures
        cy.get("[data-cy='link-:fgroup/structures'] a").first().click()

        // Stuctures is visible
        cy.get("[data-ident=':fgroup/structures']");
    })

})
