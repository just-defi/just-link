import xhr from "axios";

export const LOGIN = "LOGIN";

export const setLogin = (result) => ({
  type: LOGIN,
  result,
});


export const login = (username, password) => async (dispatch, getState) => {
  /*
    let {result} = await xhr.post("/api/uploadLogo", {
      username: 'admin',
      password: 'admin'
    });
  */
  let account = {
    username: username,
    password: password,
  }
  localStorage.setItem('authentication', 'true');
  localStorage.setItem('username', username);
  localStorage.setItem('password', password);
  await dispatch(setLogin({account: account, authentication: true}));

};