// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
Cypress.Commands.add("dummyLogin", (name) => {
    cy.visit("#/login")
    cy.window().then((win) => {
        win.teet.login.login_controller.test_login(
            Cypress.env("SITE_PASSWORD"),
            name)
    })

    // Redirects to map page
    cy.location("hash").should("eq", "#/projects/map")

})

// Select language
Cypress.Commands.add("selectLanguage", (lang) => {
  cy.get("#language-select")
    .should((select) => {
      Cypress.dom.isAttached(select)
    })
    .select(lang);
})

// Create random name with prefix and assign it
Cypress.Commands.add("randomName", (contextName, prefix) => {
    const r = Math.floor(Math.random() * 100000)
    const randomName = `${prefix}${r}`
    console.log("WRAP ", randomName, " AS ", contextName)
    cy.wrap(randomName).as(contextName);
})

// Input to TEET form text input
Cypress.Commands.add("formInput", (...attrAndText) => {
    for(let i = 0; i < attrAndText.length/2; i++) {
        const attr = attrAndText[i*2+0]
        const text = attrAndText[i*2+1]

        // If text is "[:some.keyword/value]", then this is a selection
        // that should be made in a dropdown list
        const selection = /^\[(:.*)\]$/.exec(text)
        if(selection != null) {
            // Click open the dropdown
            const select = `div[data-form-attribute='${attr}'] select`
            const option = `${select} option[data-item='${selection[1]}']`

            cy.get(option).then(($opt) => {
                cy.get(select).select($opt.attr("value"))
            })

        } else if(text.startsWith("RTE:")) {
            // Rich Text Editor field
            cy.get(`div[data-form-attribute='${attr}'] [contenteditable]`).type(text.substr(4))
        } else {
            // Regular text, just type it in
            cy.get(`div[data-form-attribute='${attr}'] input`).type(text)
        }
    }
})

Cypress.Commands.add("formSubmit", () => {
    cy.get("form button.submit").click()
})

Cypress.Commands.add("formCancel", () => {
    cy.get("form button.cancel").click()
})


Cypress.Commands.add("projectByName", (projectName) => {
  cy.visit("#/projects/list?row-filter=all")
  cy.get("input[id='filter input for :thk.project/project-name']").type(projectName)
  cy.get("td").contains(projectName).click()

  // check project page is rendered
  cy.get("h1").contains(projectName)
})

Cypress.Commands.add("backendCommand", (commandName, payload) => {
    cy.window().then((win) => {
        win.teet.common.common_controller.test_command(commandName, payload)
    })
})

// Opts must have:
// - fixturePath: string path to file under fixtures
// - mimeType: MIME type of the file
// - inputSelector: selector string for cy.get for finding the file input
// Optionally:
// - fileName: file name, if other than fixture path
Cypress.Commands.add("uploadFile", (opts) => {
  cy.fixture(opts.fixturePath, "base64").then(fileContent => {
    const blob = Cypress.Blob.base64StringToBlob(fileContent, opts.mimeType)

    const file = new File([blob], opts.fileName || opts.fixturePath, {type: opts.mimeType})
    const list = new DataTransfer()

    list.items.add(file)

    const fileList = list.files

    cy.get(opts.inputSelector).then($el => {
      $el[0].files = fileList
      $el[0].dispatchEvent(new Event("change", {bubbles: true}))
    })
  })
})
//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })
