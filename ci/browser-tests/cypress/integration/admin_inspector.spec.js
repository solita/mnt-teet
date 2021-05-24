context("Admin inspector", () => {
    before(() => {
        cy.dummyLogin("benjamin")
        cy.visit("#/admin/inspect/user-EE12345678900") // inspect danny user entity
    })

    it("has expected content", () => {

        cy.get("td").contains(":user/id")
        cy.get("td").contains("4c8ec140-4bd8-403b-866f-d2d5db9bdf74")

        cy.get("button").contains("Show change history").click({force: true})
        cy.get("button").contains("Show change history").should("not.exist")

        cy.get(".inspector-history")

    })

})
