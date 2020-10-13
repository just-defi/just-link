import {LOGIN} from "../actions/app";


let initial_authentication = localStorage.getItem('authentication');
let initial_username = localStorage.getItem('username');
let initial_password = localStorage.getItem('password');

console.log();

const initialState = {
  account: {
    username: initial_username,
    password: initial_password,
  },
  authentication: initial_authentication === 'true'
};

export function appReducer(state = initialState, action) {

  switch (action.type) {

    case LOGIN: {

      return {
        account: action.result.account,
        authentication: action.result.authentication
      };
    }

    default:
      return state;
  }
}
