context('Cooperation', () => {
    beforeEach(() => {
        cy.dummyLogin("Carla")
    })

    it("Cooperation menu visible in project", () => {
        cy.get(".left-menu-projects-list").click()
        cy.get("td").contains("cooperation test").click()

        // check project page is rendered
        cy.get("h1").contains("cooperation test")

        // open menu and select cooperation
        cy.get("button.project-menu").click()
        cy.get("li.project-menu-item-cooperation").click()
    })

})
