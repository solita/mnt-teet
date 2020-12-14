context("Mentions input", function() {
    before(() => {
        cy.dummyLogin("carla")
        cy.visit("#/components?show=mentions")
    })

    it("Empty input", () => {

        let sel = "[data-cy='mentions-empty'] textarea"

        cy.get(sel).type("testing @da")

        // expect suggestion for danny d manager to be present
        cy.get(sel).get(".comment-textarea__suggestions__item").contains("Danny D. Manager").click()

        cy.get(sel).should("have.value", "testing @Danny D. Manager")

        // type 1 backspace and it should clear the whole mention
        cy.get(sel).type("{backspace}")
        cy.get(sel).should("have.value", "testing ")

    })

    it("Existing input", () => {

        let sel = "[data-cy='mentions-existing'] textarea"

        cy.get(sel).should("have.value", "Hey @Carla Consultant how are you?")

        // type more text, it should go in the end (tests for caret jumping bug)
        cy.get(sel).type(" More text")
        cy.get(sel).should("have.value", "Hey @Carla Consultant how are you? More text")

    })
})
